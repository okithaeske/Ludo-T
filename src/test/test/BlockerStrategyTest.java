package test.test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.Piece;
import player.GreenPlayer;
import player.strategy.BlockerStrategy;
import player.strategy.PieceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockerStrategy (Green)")
class BlockerStrategyTest extends BaseTest {

    private GreenPlayer green;
    private Board board;
    private PieceSelectionStrategy strategy;

    @BeforeEach
    void setUp() {
        green = new GreenPlayer();
        board = new Board();
        strategy = new BlockerStrategy(green);
    }

    // â”€â”€ Base exit on roll 6 â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("should_exitBase_when_rollIsSixAndBasePieceExists")
    void should_exitBase_when_rollIsSixAndBasePieceExists() {
        // Arrange â€” all pieces at BASE (default)

        // Act
        Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

        // Assert
        assertFalse(selected.isNull());
        assertEquals(PieceState.BASE, selected.getState());
    }

    // â”€â”€ Block formation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("should_moveToFormBlock_when_friendlyPieceIsAtTargetCell")
    void should_moveToFormBlock_when_friendlyPieceIsAtTargetCell() {
        // Arrange:
        //   G1 at 10 (CW), G2 at 11 (CW)
        //   roll = 1 â†’ G1's target = 11 where G2 already is â†’ block formation
        Piece[] pieces = green.getPieces();

        pieces[0].setState(PieceState.ACTIVE);
        pieces[0].moveTo(10);
        pieces[0].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[0], 10);

        pieces[1].setState(PieceState.ACTIVE);
        pieces[1].moveTo(11);
        pieces[1].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[1], 11);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert â€” G1 should be selected to stack onto G2
        assertEquals(pieces[0], selected);
    }

    // â”€â”€ No valid move â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix")
    void should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix() {
        // Arrange â€” all pieces at BASE, roll = 3

        // Act
        Piece selected = strategy.choosePiece(3, board);

        // Assert
        assertTrue(selected.isNull());
    }

    // â”€â”€ Avoid capture fallback â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("should_moveLeastAdvancedPiece_when_avoidingCapture")
    void should_moveLeastAdvancedPiece_when_avoidingCapture() {
        // Arrange:
        //   G1 at 5  (CW), GREEN_APPROACH=37: dist = (37-5+52)%52 = 32 (far from home)
        //   G2 at 30 (CW): dist = (37-30+52)%52 = 7  (close to home)
        //   No opponents â†’ no capture needed.
        //   BlockerStrategy avoidCapture() picks piece furthest from home (G1).
        Piece[] pieces = green.getPieces();

        pieces[0].setState(PieceState.ACTIVE);
        pieces[0].moveTo(5);
        pieces[0].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[0], 5);

        pieces[1].setState(PieceState.ACTIVE);
        pieces[1].moveTo(30);
        pieces[1].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[1], 30);

        // Act roll 1 (no block-formation move possible with only one piece at each cell)
        Piece selected = strategy.choosePiece(1, board);

        // Assert â€” G1 (pieces[0]) is furthest from home â†’ least advanced â†’ selected
        assertEquals(pieces[0], selected);
    }
}

