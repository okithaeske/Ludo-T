package test.test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.RedPlayer;
import player.strategy.AggressiveStrategy;
import player.strategy.PieceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AggressiveStrategy (Red)")
class AggressiveStrategyTest extends BaseTest {

    private RedPlayer red;
    private Board board;
    private PieceSelectionStrategy strategy;

    @BeforeEach
    void setUp() {
        red = new RedPlayer();
        board = new Board();
        strategy = new AggressiveStrategy(red);
    }

    /** Helper: activate a player piece at the given position (CW direction). */
    private Piece activatePieceAt(int pieceIndex, int position) {
        Piece piece = red.getPieces()[pieceIndex];
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, position);
        return piece;
    }

    /** Helper: place a standalone opponent piece on the board. */
    private Piece placeOpponentAt(Colour colour, int position) {
        Piece opponent = new Piece(colour.name().charAt(0) + "1", colour);
        opponent.setState(PieceState.ACTIVE);
        opponent.moveTo(position);
        opponent.assignInitialDirection(Direction.CW);
        board.placePiece(opponent, position);
        return opponent;
    }

    //  Capture priority
    @Test
    @DisplayName("should_selectAttacker_when_opponentIsInCaptureRange")
    void should_selectAttacker_when_opponentIsInCaptureRange() {
        // Arrange Red at 10 (CW), Green single piece at 11; roll 1 â†’ target = 11
        Piece redPiece = activatePieceAt(0, 10);
        placeOpponentAt(Colour.GREEN, 11);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert
        assertEquals(redPiece, selected);
    }

    @Test
    @DisplayName("should_pickAttackerTargetingOpponentClosestToTheirHome_when_multipleCapturesAvailable")
    void should_pickAttackerTargetingOpponentClosestToTheirHome_when_multipleCapturesAvailable() {
        // Arrange:
        //   R1 at 10 (CW), roll 1 â†’ targets Green at 11.
        //       Green at 11 (CW), GREEN_APPROACH=37: dist = (37-11+52)%52 = 26
        //   R2 at 20 (CW), roll 1 â†’ targets Green at 21.
        //       Green at 21 (CW): dist = (37-21+52)%52 = 16  â† closer to Green's home
        // AggressiveStrategy must return R2 because its target is the nearer Green.
        Piece r1 = activatePieceAt(0, 10);
        Piece r2 = activatePieceAt(1, 20);
        placeOpponentAt(Colour.GREEN, 11);
        placeOpponentAt(Colour.GREEN, 21);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert
        assertEquals(r2, selected);
    }

    // No valid move
    @Test
    @DisplayName("should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix")
    void should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix() {
        // Arrange â€” all Red pieces remain at BASE (default), roll=3

        // Act
        Piece selected = strategy.choosePiece(3, board);

        // Assert
        assertTrue(selected.isNull());
    }

    //  Base exit on roll 6 Test
    @DisplayName("should_bringBasePiece_when_rollIsSix_andNoBoardPiecesExist")
    void should_bringBasePiece_when_rollIsSix_andNoBoardPiecesExist() {
        // Arrange â€” all pieces at BASE, no board pieces

        // Act
        Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

        // Assert â€” must not be null; must be the first base piece
        assertFalse(selected.isNull());
        assertEquals(PieceState.BASE, selected.getState());
    }
}

