package test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Block;
import model.Piece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Block")
class BlockTest extends BaseTest {

    //Size
    @Test
    @DisplayName("should_reportCorrectSize_when_piecesAdded")
    void should_reportCorrectSize_when_piecesAdded() {
        // Arrange
        Block block = new Block(10, Direction.CW);
        block.addPiece(new Piece("R1", Colour.RED));
        block.addPiece(new Piece("R2", Colour.RED));

        // Assert
        Assertions.assertEquals(2, block.getSize());
    }

    @Test
    @DisplayName("should_reportZeroSize_when_noPiecesAdded")
    void should_reportZeroSize_when_noPiecesAdded() {
        // Arrange
        Block block = new Block(10, Direction.CW);

        // Assert
        Assertions.assertEquals(0, block.getSize());
    }

    // canBeCaptured()
    @Test
    @DisplayName("should_returnTrue_when_attackerSizeEqualsDefenderSize")
    void should_returnTrue_when_attackerSizeEqualsDefenderSize() {
        // Arrange
        Block defender = new Block(10, Direction.CW);
        defender.addPiece(new Piece("G1", Colour.GREEN));
        defender.addPiece(new Piece("G2", Colour.GREEN));

        Block attacker = new Block(5, Direction.CW);
        attacker.addPiece(new Piece("R1", Colour.RED));
        attacker.addPiece(new Piece("R2", Colour.RED));

        // Assert
        Assertions.assertTrue(defender.canBeCaptured(attacker));
    }

    // breakBlock() direction restore (Rule T-5)
    @Test
    @DisplayName("should_restoreOriginalDirection_when_blockIsBroken")
    void should_restoreOriginalDirection_when_blockIsBroken() {
        // Arrange â€” piece assigned CCW as original direction, then temporarily redirected CW
        // (simulating a Gamma teleport that flipped the direction while in a block).
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.assignInitialDirection(Direction.CCW); // originalDirection = CCW
        piece.setDirection(Direction.CW);            // current direction flipped to CW

        Block block = new Block(10, Direction.CW);
        block.addPiece(piece);

        // Act
        block.breakBlock(piece);

        // Assert â€” direction must be restored to the original CCW
        Assertions.assertEquals(Direction.CCW, piece.getDirection());
        Assertions.assertEquals(0, block.getSize());
    }

    @Test
    @DisplayName("should_returnFalse_when_attackerSizeDiffersFromDefenderSize")
    void should_returnFalse_when_attackerSizeDiffersFromDefenderSize() {
        // Arrange
        Block defender = new Block(10, Direction.CW);
        defender.addPiece(new Piece("G1", Colour.GREEN));
        defender.addPiece(new Piece("G2", Colour.GREEN));

        Block attacker = new Block(5, Direction.CW);
        attacker.addPiece(new Piece("R1", Colour.RED));

        // Assert
        Assertions.assertFalse(defender.canBeCaptured(attacker));
    }
}

