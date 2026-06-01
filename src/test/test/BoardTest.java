package test.test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Block;
import model.Board;
import model.GameConstants;
import model.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Board")
class BoardTest extends BaseTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    //placePiece / removePiece
    @Test
    @DisplayName("should_trackPiece_when_placePieceCalled")
    void should_trackPiece_when_placePieceCalled() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);

        // Act
        board.placePiece(piece, 10);

        // Assert
        assertEquals(1, board.getPiecesAt(10).size());
    }

    @Test
    @DisplayName("should_removePiece_when_removePieceCalled")
    void should_removePiece_when_removePieceCalled() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        board.placePiece(piece, 10);

        // Act
        board.removePiece(piece, 10);

        // Assert
        assertEquals(0, board.getPiecesAt(10).size());
    }

    @Test
    @DisplayName("should_returnEmptyList_when_cellIsEmpty")
    void should_returnEmptyList_when_cellIsEmpty() {
        // Assert
        assertTrue(board.getPiecesAt(5).isEmpty());
    }

    // getPiecesAt
    @Test
    @DisplayName("should_returnCorrectPieces_when_multiplePiecesAtSameCell")
    void should_returnCorrectPieces_when_multiplePiecesAtSameCell() {
        // Arrange
        Piece r1 = new Piece("R1", Colour.RED);
        Piece r2 = new Piece("R2", Colour.RED);
        board.placePiece(r1, 15);
        board.placePiece(r2, 15);

        // Assert
        assertEquals(2, board.getPiecesAt(15).size());
        assertTrue(board.getPiecesAt(15).contains(r1));
        assertTrue(board.getPiecesAt(15).contains(r2));
    }

    // getBlockAt

    @Test
    @DisplayName("should_returnBlock_when_twoSameColourPiecesAtCell")
    void should_returnBlock_when_twoSameColourPiecesAtCell() {
        // Arrange
        Piece r1 = new Piece("R1", Colour.RED);
        Piece r2 = new Piece("R2", Colour.RED);
        board.placePiece(r1, 20);
        board.placePiece(r2, 20);

        // Act
        Block block = board.getBlockAt(20, Colour.RED, Direction.CW);

        // Assert
        assertNotNull(block);
        assertEquals(2, block.getSize());
    }

    @Test
    @DisplayName("should_returnNull_when_onlyOneSameColourPieceAtCell")
    void should_returnNull_when_onlyOneSameColourPieceAtCell() {
        // Arrange
        Piece r1 = new Piece("R1", Colour.RED);
        board.placePiece(r1, 20);

        // Act
        Block block = board.getBlockAt(20, Colour.RED, Direction.CW);

        // Assert
        assertNull(block);
    }

    @Test
    @DisplayName("should_returnNull_when_piecesAtCellAreDifferentColours")
    void should_returnNull_when_piecesAtCellAreDifferentColours() {
        // Arrange
        Piece r1 = new Piece("R1", Colour.RED);
        Piece g1 = new Piece("G1", Colour.GREEN);
        board.placePiece(r1, 20);
        board.placePiece(g1, 20);

        // Act
        Block block = board.getBlockAt(20, Colour.RED, Direction.CW);

        // Assert
        assertNull(block);
    }

    //distanceToHome
    @Test
    @DisplayName("should_calculateCorrectDistance_when_pieceIsMovingCW")
    void should_calculateCorrectDistance_when_pieceIsMovingCW() {
        // Arrange â€” Red approach = 24, piece at 20, CW distance = (24-20+52)%52 = 4
        Piece piece = new Piece("R1", Colour.RED);
        piece.moveTo(20);
        piece.setState(PieceState.ACTIVE);
        // direction is CW by default

        // Act
        int distance = board.distanceToHome(piece);

        // Assert
        assertEquals(4, distance);
    }

    @Test
    @DisplayName("should_calculateCorrectDistance_when_pieceIsMovingCCW")
    void should_calculateCorrectDistance_when_pieceIsMovingCCW() {
        // Arrange â€” Red approach = 24, piece at 26, CCW distance = (26-24+52)%52 = 2
        Piece piece = new Piece("R1", Colour.RED);
        piece.moveTo(26);
        piece.setState(PieceState.ACTIVE);
        piece.setDirection(Direction.CCW);

        // Act
        int distance = board.distanceToHome(piece);

        // Assert
        assertEquals(2, distance);
    }

    // Start cells
    @Test
    @DisplayName("should_mapCorrectStartCells_when_queried")
    void should_mapCorrectStartCells_when_queried() {
        // Assert
        assertEquals(GameConstants.RED_START,    board.getStartX(Colour.RED));
        assertEquals(GameConstants.GREEN_START,  board.getStartX(Colour.GREEN));
        assertEquals(GameConstants.YELLOW_START, board.getStartX(Colour.YELLOW));
        assertEquals(GameConstants.BLUE_START,   board.getStartX(Colour.BLUE));
    }

    // Approach cells
    @Test
    @DisplayName("should_mapCorrectApproachCells_when_queried")
    void should_mapCorrectApproachCells_when_queried() {
        // Assert
        assertEquals(GameConstants.RED_APPROACH,    board.getApproach(Colour.RED));
        assertEquals(GameConstants.GREEN_APPROACH,  board.getApproach(Colour.GREEN));
        assertEquals(GameConstants.YELLOW_APPROACH, board.getApproach(Colour.YELLOW));
        assertEquals(GameConstants.BLUE_APPROACH,   board.getApproach(Colour.BLUE));
    }
}

