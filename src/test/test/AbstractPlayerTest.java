package test.test;

import enums.PieceState;
import model.Piece;
import player.AbstractPlayer;
import player.RedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractPlayer")
class AbstractPlayerTest extends BaseTest {

    private AbstractPlayer player;

    @BeforeEach
    void setUp() {
        player = new RedPlayer();
    }

    // getPiecesOnBoard

    @Test
    @DisplayName("should_returnEmptyList_when_allPiecesAreAtBase")
    void should_returnEmptyList_when_allPiecesAreAtBase() {
        // Arrange â€” all pieces default to BASE

        // Assert
        assertTrue(player.getPiecesOnBoard().isEmpty());
    }

    @Test
    @DisplayName("should_returnOnlyActivePieces_when_getPiecesOnBoardCalled")
    void should_returnOnlyActivePieces_when_getPiecesOnBoardCalled() {
        // Arrange â€” activate pieces[0], leave rest at BASE
        player.getPieces()[0].setState(PieceState.ACTIVE);

        // Assert
        assertEquals(1, player.getPiecesOnBoard().size());
        assertTrue(player.getPiecesOnBoard().contains(player.getPieces()[0]));
    }

    //getPiecesAtBase

    @Test
    @DisplayName("should_returnAllPieces_when_allPiecesAreAtBase")
    void should_returnAllPieces_when_allPiecesAreAtBase() {
        // Arrange all pieces default to BASE

        // Assert
        assertEquals(4, player.getPiecesAtBase().size());
    }

    @Test
    @DisplayName("should_excludeActivePieces_when_getPiecesAtBaseCalled")
    void should_excludeActivePieces_when_getPiecesAtBaseCalled() {
        // Arrange â€” move one piece to ACTIVE
        player.getPieces()[0].setState(PieceState.ACTIVE);

        // Assert
        assertEquals(3, player.getPiecesAtBase().size());
        assertFalse(player.getPiecesAtBase().contains(player.getPieces()[0]));
    }

    // allHome

    @Test
    @DisplayName("should_returnTrue_when_allPiecesAreHome")
    void should_returnTrue_when_allPiecesAreHome() {
        // Arrange
        for (Piece piece : player.getPieces()) {
            piece.setState(PieceState.HOME);
        }

        // Assert
        assertTrue(player.allHome());
    }

    @Test
    @DisplayName("should_returnFalse_when_onePieceIsNotHome")
    void should_returnFalse_when_onePieceIsNotHome() {
        // Arrange set 3 pieces to HOME, leave 1 at BASE
        Piece[] pieces = player.getPieces();
        pieces[0].setState(PieceState.HOME);
        pieces[1].setState(PieceState.HOME);
        pieces[2].setState(PieceState.HOME);
        // pieces[3] stays BASE

        // Assert
        assertFalse(player.allHome());
    }
}

