package engine;

import enums.GameMode;
import logger.GameEventPublisher;
import logger.Logger;
import model.Board;
import model.GameConstants;
import model.MysteryCell;
import player.AbstractPlayer;
import player.PlayerFactory;

import java.util.List;

/**
 * Facade over the game subsystems.
 *
 * <p>External code calls only {@link #startGame()}; all turn execution, effect handling,
 * win tracking, and rule validation are delegated to the collaborator classes.
 *
 * @see TurnExecutor
 * @see EffectHandler
 * @see WinTracker
 * @see RuleEngine
 */
public class GameEngine {

    private final Board board;
    private final TurnManager turnManager;
    private final RuleEngine ruleEngine;
    private final List<AbstractPlayer> players;
    private final GameEventPublisher publisher;
    private final TurnExecutor turnExecutor;
    private final WinTracker winTracker;
    private int roundNumber;
    private final GameMode gameMode;

    public GameEngine(GameMode gameMode) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.publisher = new GameEventPublisher();
        this.publisher.addListener(new Logger());
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players);
        this.roundNumber = 0;

        EffectHandler effectHandler = new EffectHandler(board, turnManager, publisher);
        this.winTracker = new WinTracker(players, publisher);
        this.turnExecutor = new TurnExecutor(board, ruleEngine, turnManager, publisher,
                effectHandler, gameMode);
    }

    /**
     * Single public entry point. Publishes start events, picks the first player, then
     * drives the game loop until {@link WinTracker#isGameOver()} is true.
     *
     * @see #runGameLoop()
     * @see #determineFirstPlayer()
     */
    public void startGame() {
        publisher.publishGameStart();
        publisher.publishGameInitialisation(players);
        determineFirstPlayer();
        runGameLoop();
    }

    /**
     * Rolls dice for all players (with tie-breaking) and sets the resulting turn order.
     *
     * @see FirstPlayerSelector#selectFirstPlayer()
     * @see TurnManager#setOrder(player.AbstractPlayer)
     */
    public void determineFirstPlayer() {
        FirstPlayerSelector selector = new FirstPlayerSelector(players, turnManager, publisher);
        AbstractPlayer firstPlayer = selector.selectFirstPlayer();
        turnManager.setOrder(firstPlayer);
    }

    /**
     * Iterates {@link #executeRound()} until the game is over, then publishes final standings.
     *
     * @see #executeRound()
     * @see WinTracker#isGameOver()
     */
    public void runGameLoop() {
        while (!winTracker.isGameOver()) {
            executeRound();
        }
        publisher.publishGameResult(winTracker.getFinishingOrder());
    }

    /**
     * Runs one full round: mystery-cell tick, then one turn per still-active player
     * (including extra-roll continuations). Returns early if the game ends mid-round.
     *
     * @see TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
     * @see WinTracker#checkWinCondition(player.AbstractPlayer)
     */
    public void executeRound() {
        roundNumber++;
        handleMysteryCell();

        for (AbstractPlayer player : players) {
            if (winTracker.getFinishingOrder().contains(player)) continue;
            turnExecutor.executeTurn(player, false);
            while (turnManager.isExtraRollPending()) {
                turnManager.clearExtraRoll();
                turnExecutor.executeTurn(player, true);
            }
            turnManager.advanceToNextPlayer();
            if (winTracker.checkWinCondition(player)) return;
        }

        publisher.publishRoundSummary(players, board.getMysteryCell());
        publisher.publishRoundComplete();
    }

    private void handleMysteryCell() {
        if (isLudoT() && isMysterySpawnRound()) {
            spawnOrRelocateMysteryCell();
        }
    }

    private boolean isMysterySpawnRound() {
        return roundNumber >= GameConstants.MYSTERY_SPAWN_ROUND;
    }

    // Fix 9: pass board to tick() so relocation avoids occupied cells
    private void spawnOrRelocateMysteryCell() {
        if (board.getMysteryCell() == null) {
            board.setMysteryCell(new MysteryCell(GameConstants.NO_POSITION));
            board.getMysteryCell().spawn(board);
        } else {
            board.getMysteryCell().tick(board);
        }
        publisher.publishMysterySpawn(board.getMysteryPosition());
    }

    public boolean isLudoT() {
        return gameMode == GameMode.LUDO_T;
    }
}
