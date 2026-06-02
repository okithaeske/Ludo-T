package test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.MysteryCell;
import model.Piece;
import player.BluePlayer;
import player.strategy.MysteryHunterStrategy;
import player.strategy.PieceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysteryHunterStrategy (Blue)")
class MysteryHunterStrategyTest extends BaseTest {

    private BluePlayer blue;
    private Board board;

    @BeforeEach
    void setUp() {
        blue = new BluePlayer();
        board = new Board();
    }

    /**
     * Activates the mystery cell at a random empty position using the seeded RNG.
     * Returns the cell index the mystery landed on.
     */
    private int spawnMystery() {
        // Need at least one piece on the board to allow spawning
        Piece anchor = new Piece("A1", Colour.RED);
        anchor.setState(PieceState.ACTIVE);
        anchor.moveTo(0);
        board.placePiece(anchor, 0);

        MysteryCell mystery = new MysteryCell(GameConstants.NO_POSITION);
        mystery.spawn(board);
        board.setMysteryCell(mystery);
        return mystery.getPosition();
    }

    /** Places a Blue piece at the given position with the given direction. */
    private Piece placeBluePieceAt(int index, int position, Direction direction) {
        Piece piece = blue.getPieces()[index];
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(direction);
        board.placePiece(piece, position);
        return piece;
    }

    // Base exit
    @Test
    @DisplayName("should_exitBase_when_rollIsSixAndBasePieceExists")
    void should_exitBase_when_rollIsSixAndBasePieceExists() {
        // Arrange Ã¢â‚¬â€ all pieces at BASE (default)
        PieceSelectionStrategy strategy = new MysteryHunterStrategy(blue);

        // Act
        Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

        // Assert
        Assertions.assertFalse(selected.isNull());
        Assertions.assertEquals(PieceState.BASE, selected.getState());
    }

    // CCW targets mystery
    @Test
    @DisplayName("should_selectCCWPiece_when_itIsHeadingTowardsMysteryCell")
    void should_selectCCWPiece_when_itIsHeadingTowardsMysteryCell() {
        // Arrange Ã¢â‚¬â€ spawn mystery at M, then place Blue piece 10 steps ahead of M in CCW.
        // CCW distance from piece to M = (piece_pos - M + 52) % 52 = 10 Ã¢â€°Â¤ 26 Ã¢â€ â€™ heading towards.
        int mysteryPos = spawnMystery();
        int piecePos = (mysteryPos + 10) % GameConstants.BOARD_SIZE;
        // Make sure piece doesn't land on the anchor piece at 0
        if (piecePos == 0) piecePos = (piecePos + 1) % GameConstants.BOARD_SIZE;

        Piece bluePiece = placeBluePieceAt(0, piecePos, Direction.CCW);
        PieceSelectionStrategy strategy = new MysteryHunterStrategy(blue);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert
        Assertions.assertEquals(bluePiece, selected);
    }

    // CW avoids mystery
    @Test
    @DisplayName("should_avoidMysteryCell_when_CWPieceWouldLandOnIt")
    void should_avoidMysteryCell_when_CWPieceWouldLandOnIt() {
        // Arrange:
        //   Spawn mystery at M.
        //   B1 (CW) at (M - 1 + 52) % 52 Ã¢â‚¬â€ with roll 1 it lands exactly on M.
        //   B2 (CW) at (M + 5) % 52    Ã¢â‚¬â€ with roll 1 it lands at M+6, safely away.
        // Strategy should skip B1 and return B2.
        int mysteryPos = spawnMystery();

        int b1Pos = (mysteryPos - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        int b2Pos = (mysteryPos + 5) % GameConstants.BOARD_SIZE;

        // Guard against collisions with the anchor at 0
        if (b1Pos == 0) b1Pos = (b1Pos + GameConstants.BOARD_SIZE - 1) % GameConstants.BOARD_SIZE;
        if (b2Pos == 0) b2Pos = (b2Pos + 1) % GameConstants.BOARD_SIZE;

        placeBluePieceAt(0, b1Pos, Direction.CW);
        Piece b2 = placeBluePieceAt(1, b2Pos, Direction.CW);

        PieceSelectionStrategy strategy = new MysteryHunterStrategy(blue);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert Ã¢â‚¬â€ B1 is avoided; B2 (or any non-mystery-landing piece) is returned
        Assertions.assertNotEquals(blue.getPieces()[0], selected);
        Assertions.assertFalse(selected.isNull());
    }

    // Round-robin cycling
    @Test
    @DisplayName("should_cycleToNextPiece_when_calledConsecutively")
    void should_cycleToNextPiece_when_calledConsecutively() {
        // Arrange:
        //   Spawn mystery at M.
        //   Both B1 and B2 are CCW, 10 and 15 steps ahead of M Ã¢â‚¬â€ both target mystery.
        //   Strategy should yield B1, then B2, then B1 again (cycle).
        int mysteryPos = spawnMystery();

        int pos1 = (mysteryPos + 10) % GameConstants.BOARD_SIZE;
        int pos2 = (mysteryPos + 15) % GameConstants.BOARD_SIZE;
        if (pos1 == 0) pos1 = (pos1 + 1) % GameConstants.BOARD_SIZE;
        if (pos2 == 0) pos2 = (pos2 + 2) % GameConstants.BOARD_SIZE;
        if (pos1 == pos2) pos2 = (pos2 + 1) % GameConstants.BOARD_SIZE;

        Piece b1 = placeBluePieceAt(0, pos1, Direction.CCW);
        Piece b2 = placeBluePieceAt(1, pos2, Direction.CCW);

        PieceSelectionStrategy strategy = new MysteryHunterStrategy(blue);

        // Act
        Piece first  = strategy.choosePiece(1, board);
        Piece second = strategy.choosePiece(1, board);
        Piece third  = strategy.choosePiece(1, board);

        // Assert Ã¢â‚¬â€ pieces alternate in round-robin fashion
        Assertions.assertEquals(b1, first);
        Assertions.assertEquals(b2, second);
        Assertions.assertEquals(b1, third);
    }
}

