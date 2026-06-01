package test.test;

import enums.Colour;
import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.Piece;
import player.YellowPlayer;
import player.strategy.PieceSelectionStrategy;
import player.strategy.RacerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RacerStrategy (Yellow)")
class RacerStrategyTest extends BaseTest {

    private YellowPlayer yellow;
    private Board board;
    private PieceSelectionStrategy strategy;

    @BeforeEach
    void setUp() {
        yellow = new YellowPlayer();
        board = new Board();
        strategy = new RacerStrategy(yellow);
    }

    // Base exit
    @Test
    @DisplayName("should_alwaysExitBase_when_rollIsSixAndBasePieceExists")
    void should_alwaysExitBase_when_rollIsSixAndBasePieceExists() {
        // Arrange â€” all pieces at BASE (default)

        // Act
        Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

        // Assert
        assertFalse(selected.isNull());
        assertEquals(PieceState.BASE, selected.getState());
    }

    // Closest to home
    @Test
    @DisplayName("should_movePieceClosestToHome_when_noCaptureIsAvailable")
    void should_movePieceClosestToHome_when_noCaptureIsAvailable() {
        // Arrange:
        //   Y1 at 5  (CW), YELLOW_APPROACH=50: dist = (50-5+52)%52 = 45 (far)
        //   Y2 at 48 (CW): dist = (50-48+52)%52 = 2 (close)
        // No opponents on board â†’ no capture available.
        Piece[] pieces = yellow.getPieces();

        pieces[0].setState(PieceState.ACTIVE);
        pieces[0].moveTo(5);
        pieces[0].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[0], 5);

        pieces[1].setState(PieceState.ACTIVE);
        pieces[1].moveTo(48);
        pieces[1].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[1], 48);

        // Act â€” roll 1 (no opponent at 6 or 49)
        Piece selected = strategy.choosePiece(1, board);

        // Assert â€” Y2 (pieces[1]) is closer to Yellow's home
        assertEquals(pieces[1], selected);
    }

    // Capture for home qualification
    @Test
    @DisplayName("should_captureOpponent_when_pieceNeedsACaptureForHomeEntry")
    void should_captureOpponent_when_pieceNeedsACaptureForHomeEntry() {
        // Arrange â€” Y1 at 10, opponent at 11; Y1 has 0 captures (needs one)
        Piece[] pieces = yellow.getPieces();
        pieces[0].setState(PieceState.ACTIVE);
        pieces[0].moveTo(10);
        pieces[0].assignInitialDirection(Direction.CW);
        board.placePiece(pieces[0], 10);

        Piece opponent = new Piece("G1", Colour.GREEN);
        opponent.setState(PieceState.ACTIVE);
        opponent.moveTo(11);
        opponent.assignInitialDirection(Direction.CW);
        board.placePiece(opponent, 11);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert â€” must pick Y1 to capture the opponent
        assertEquals(pieces[0], selected);
    }
}

