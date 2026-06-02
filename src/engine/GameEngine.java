package engine;

import engine.command.ExecuteTurnCommand;
import engine.command.TurnCommand;
import enums.GameMode;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.Board;
import player.AbstractPlayer;
import player.PlayerFactory;

import java.util.List;

/**
 * <b>Facade</b> over the game subsystems: {@link Board}, {@link RuleEngine},
 * {@link TurnManager}, {@link TurnExecutor}, {@link EffectHandler}, and
 * {@link WinTracker}. Clients call only {@link #startGame()}.
 *
 * <p>Constructed exclusively via {@link GameEngineBuilder} (Builder pattern) which
 * injects {@link GameEventListener} implementations, satisfying the
 * Dependency Inversion Principle.
 *
 * <p>Turn execution uses the <b>Command pattern</b> ({@link TurnCommand}) so
 * {@code GameEngine} remains decoupled from {@link TurnExecutor} internals.
 */
public class GameEngine {

    private final Board board;
    private final TurnManager turnManager;
    private final RuleEngine ruleEngine;
    private final List<AbstractPlayer> players;
    private final GameEventPublisher publisher;
    private final TurnExecutor turnExecutor;
    private final WinTracker winTracker;
    private final GameMode gameMode;
    private final MysteryCellManager mysteryCellManager;
    private int roundNumber;

    GameEngine(GameMode gameMode, List<GameEventListener> listeners) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.publisher = new GameEventPublisher();
        listeners.forEach(publisher::addListener);
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players);
        this.roundNumber = 0;

        EffectHandler effectHandler = new EffectHandler(board, turnManager, publisher, ruleEngine, players);
        this.winTracker = new WinTracker(players, publisher);
        this.turnExecutor = new TurnExecutor(board, ruleEngine, turnManager, publisher,
                effectHandler, gameMode, players);
        this.mysteryCellManager = new MysteryCellManager(board, gameMode, publisher);
    }

    public void startGame() {
        publisher.publishGameStart();
        publisher.publishGameInitialisation(players);
        determineFirstPlayer();
        runGameLoop();
    }

    public void determineFirstPlayer() {
        FirstPlayerSelector selector = new FirstPlayerSelector(players, turnManager, publisher);
        AbstractPlayer firstPlayer = selector.selectFirstPlayer();
        turnManager.setOrder(firstPlayer);
        turnManager.resetRollStreaks();
        publisher.publishFirstPlayerSelected(firstPlayer, turnManager.getTurnOrder(), List.of());
    }

    public void runGameLoop() {
        while (!winTracker.isGameOver()) {
            executeRound();
        }
        publisher.publishGameResult(winTracker.getFinishingOrder());
    }

    /**
     * Executes one complete round for all players.
     * Uses the <b>Command pattern</b> to dispatch each turn.
     */
    public void executeRound() {
        roundNumber++;

        for (AbstractPlayer player : turnManager.getTurnOrder()) {
            if (winTracker.getFinishingOrder().contains(player)) {
                turnManager.advanceToNextPlayer();
                continue;
            }

            TurnCommand turn = new ExecuteTurnCommand(turnExecutor, player, false);
            turn.execute();

            while (turnManager.isExtraRollPending()) {
                turnManager.clearExtraRoll();
                TurnCommand extraTurn = new ExecuteTurnCommand(turnExecutor, player, true);
                extraTurn.execute();
            }

            turnManager.advanceToNextPlayer();
            if (winTracker.checkWinCondition(player)) return;
        }

        mysteryCellManager.onRoundComplete();
        publisher.publishRoundSummary(players, board.getMysteryCell());
        publisher.publishRoundComplete();
    }

    public boolean isGameOver() {
        return winTracker.isGameOver();
    }

}
