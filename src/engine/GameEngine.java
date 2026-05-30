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
    private int completedRoundsWithStandardPathPieces;
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
        this.completedRoundsWithStandardPathPieces = 0;

        EffectHandler effectHandler = new EffectHandler(board, turnManager, publisher, ruleEngine);
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
        turnManager.resetRollStreaks(); // selection rolls must not affect real game streaks
        publisher.publishFirstPlayerSelected(firstPlayer, turnManager.getTurnOrder(), List.of());
    }

    public void runGameLoop() {
        while (!winTracker.isGameOver()) {
            executeRound();
        }
        publisher.publishGameResult(winTracker.getFinishingOrder());
    }

    public void executeRound() {
        roundNumber++;

        for (AbstractPlayer player : turnManager.getTurnOrder()) {
            if (winTracker.getFinishingOrder().contains(player)) {
                turnManager.advanceToNextPlayer();
                continue;
            }

            turnExecutor.executeTurn(player, false);
            while (turnManager.isExtraRollPending()) {
                turnManager.clearExtraRoll();
                turnExecutor.executeTurn(player, true);
            }
            turnManager.advanceToNextPlayer();
            if (winTracker.checkWinCondition(player)) return;
        }

        updateMysteryCellAfterCompletedRound();
        publisher.publishRoundSummary(players, board.getMysteryCell());
        publisher.publishRoundComplete();
    }

    private void updateMysteryCellAfterCompletedRound() {
        if (!isLudoT()) return;

        MysteryCell mysteryCell = board.getMysteryCell();
        if (mysteryCell != null && mysteryCell.isActive()) {
            boolean relocated = mysteryCell.tick(board);
            if (relocated && mysteryCell.isActive()) {
                publisher.publishMysterySpawn(mysteryCell.getPosition());
            }
            return;
        }

        if (hasAnyPieceOnStandardPath()) {
            completedRoundsWithStandardPathPieces++;
        }

        if (completedRoundsWithStandardPathPieces >= GameConstants.MYSTERY_SPAWN_ROUND) {
            if (board.getMysteryCell() == null) {
                board.setMysteryCell(new MysteryCell(GameConstants.NO_POSITION));
            }
            boolean spawned = board.getMysteryCell().spawn(board);
            if (spawned) {
                publisher.publishMysterySpawn(board.getMysteryPosition());
            }
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

    public boolean isLudoT() {
        return gameMode == GameMode.LUDO_T;
    }
}
