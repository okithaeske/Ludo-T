package engine;

import engine.command.ExecuteTurnCommand;
import engine.command.TurnCommand;
import enums.GameMode;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.Board;
import model.RandomSource;
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

    GameEngine(GameMode gameMode, List<GameEventListener> listeners, RandomSource randomSource) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.publisher = new GameEventPublisher();
        listeners.forEach(publisher::addListener);
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players, randomSource);
        this.roundNumber = 0;

        EffectHandler effectHandler = new EffectHandler(board, turnManager, publisher, ruleEngine,
                players, randomSource);
        this.winTracker = new WinTracker(players, publisher);
        this.turnExecutor = new TurnExecutor(board, ruleEngine, turnManager, publisher,
                effectHandler, gameMode, players);
        this.mysteryCellManager = new MysteryCellManager(board, gameMode, publisher, randomSource);
    }

    public void startGame() {
        beginGame();
        runGameLoop();
    }

    /**
     * Performs everything {@link #startGame()} does <em>except</em> running the blocking round
     * loop: announces the game, introduces the players, and settles who goes first.
     *
     * <p>Split out so a caller that drives rounds itself — a server session ticking one round
     * at a time, or a step-through control — can set the game up without surrendering its
     * thread to {@link #runGameLoop()}.
     */
    public void beginGame() {
        publisher.publishGameStart();
        publisher.publishGameInitialisation(players);
        determineFirstPlayer();
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

    // ── Read-only accessors ──────────────────────────────────────────────────
    // Added for Assignment 2 so a caller can photograph the game between rounds without
    // reaching into the subsystems the facade hides. Every one of these is read-only: the
    // facade still owns all mutation, so exposing them does not weaken the encapsulation.

    public Board getBoard() {
        return board;
    }

    public List<AbstractPlayer> getPlayers() {
        return List.copyOf(players);
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    /** Rounds completed so far. */
    public int getRoundNumber() {
        return roundNumber;
    }

    /** Players in the order they finished; empty until someone gets all four pieces home. */
    public List<AbstractPlayer> getFinishingOrder() {
        return List.copyOf(winTracker.getFinishingOrder());
    }

    /** The player whose turn is next. */
    public AbstractPlayer getCurrentPlayer() {
        return turnManager.getCurrentPlayer();
    }

}
