package test.test;

import enums.Colour;
import enums.Direction;
import model.Block;
import model.Piece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertEquals(2, block.getSize());
    }

    @Test
    @DisplayName("should_reportZeroSize_when_noPiecesAdded")
    void should_reportZeroSize_when_noPiecesAdded() {
        // Arrange
        Block block = new Block(10, Direction.CW);

        // Assert
        assertEquals(0, block.getSize());
    }

    // â”€â”€ canBeCaptured() â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
        assertTrue(defender.canBeCaptured(attacker));
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
        assertFalse(defender.canBeCaptured(attacker));
    }
}

