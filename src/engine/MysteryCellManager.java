package engine;

import enums.GameMode;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.MysteryCell;

/**
 * Manages the mystery-cell lifecycle for Ludo-T: tracking eligible rounds,
 * spawning, ticking, and relocating the mystery cell each round.
 *
 * <p>Extracted from {@link GameEngine} so the facade delegates rather than
 * implements mystery-cell behaviour, satisfying the Single Responsibility Principle.
 */
class MysteryCellManager {

    private final Board board;
    private final GameMode gameMode;
    private final GameEventPublisher publisher;
    private int roundsWithPiecesOnBoard = 0;

    MysteryCellManager(Board board, GameMode gameMode, GameEventPublisher publisher) {
        this.board = board;
        this.gameMode = gameMode;
        this.publisher = publisher;
    }

    void onRoundComplete() {
        if (!gameMode.isLudoT()) return;

        MysteryCell mysteryCell = board.getMysteryCell();
        if (mysteryCell != null && mysteryCell.isActive()) {
            tickActive(mysteryCell);
            return;
        }

        trackRounds();
        trySpawn();
    }

    private void tickActive(MysteryCell mysteryCell) {
        boolean relocated = mysteryCell.tick(board);
        if (relocated && mysteryCell.isActive()) {
            publisher.publishMysterySpawn(mysteryCell.getPosition());
        }
    }

    private void trackRounds() {
        if (hasAnyPieceOnStandardPath()) {
            roundsWithPiecesOnBoard++;
        }
    }

    private void trySpawn() {
        if (roundsWithPiecesOnBoard < GameConstants.MYSTERY_SPAWN_ROUND) return;
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
