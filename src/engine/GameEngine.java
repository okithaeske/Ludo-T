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
    }

    public void runGameLoop() {
        while (!winTracker.isGameOver()) {
            executeRound();
        }
        publisher.publishGameResult(winTracker.getFinishingOrder());
    }

    // Fix 22/23: skip finished players, check win per turn, stop round when game over
    // Fix 11: extra roll loop per player
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
