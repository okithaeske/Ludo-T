package engine;

import engine.command.ExecuteTurnCommand;
import engine.command.TurnCommand;
import enums.GameMode;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.MysteryCell;
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
    private int roundNumber;
    private int completedRoundsWithStandardPathPieces;

    GameEngine(GameMode gameMode, List<GameEventListener> listeners) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.publisher = new GameEventPublisher();
        listeners.forEach(publisher::addListener);
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players);
        this.roundNumber = 0;
        this.completedRoundsWithStandardPathPieces = 0;

        EffectHandler effectHandler = new EffectHandler(board, turnManager, publisher, ruleEngine, players);
        this.winTracker = new WinTracker(players, publisher);
        this.turnExecutor = new TurnExecutor(board, ruleEngine, turnManager, publisher,
                effectHandler, gameMode, players);
    }

    // ── Public API ────────────────────────────────────────────────────────────

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

        updateMysteryCellAfterCompletedRound();
        publisher.publishRoundSummary(players, board.getMysteryCell());
        publisher.publishRoundComplete();
    }

    public boolean isGameOver() {
        return winTracker.isGameOver();
    }

    // ── Mystery cell lifecycle ────────────────────────────────────────────────

    private void updateMysteryCellAfterCompletedRound() {
        if (!gameMode.isLudoT()) return;

        MysteryCell mysteryCell = board.getMysteryCell();
        if (mysteryCell != null && mysteryCell.isActive()) {
            tickActiveMysteryCell(mysteryCell);
            return;
        }

        trackRoundsWithPiecesOnBoard();
        trySpawnMysteryCell();
    }

    private void tickActiveMysteryCell(MysteryCell mysteryCell) {
        boolean relocated = mysteryCell.tick(board);
        if (relocated && mysteryCell.isActive()) {
            publisher.publishMysterySpawn(mysteryCell.getPosition());
        }
    }

    private void trackRoundsWithPiecesOnBoard() {
        if (hasAnyPieceOnStandardPath()) {
            completedRoundsWithStandardPathPieces++;
        }
    }

    private void trySpawnMysteryCell() {
        if (completedRoundsWithStandardPathPieces < GameConstants.MYSTERY_SPAWN_ROUND) return;
        if (board.getMysteryCell() == null) {
            board.setMysteryCell(new MysteryCell(GameConstants.NO_POSITION));
        }
        boolean spawned = board.getMysteryCell().spawn(board);
        if (spawned) {
            publisher.publishMysterySpawn(board.getMysteryPosition());
        }
    }

    private boolean hasAnyPieceOnStandardPath() {
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            if (board.isOccupied(i)) {
                return true;
            }
        }
        return false;
    }
}
