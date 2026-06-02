package test;

import engine.WinTracker;
import enums.PieceState;
import logger.GameEventPublisher;
import model.GameConstants;
import model.Piece;
import player.AbstractPlayer;
import player.BluePlayer;
import player.GreenPlayer;
import player.RedPlayer;
import player.YellowPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WinTracker")
class WinTrackerTest extends BaseTest {

    private RedPlayer    p1;
    private GreenPlayer  p2;
    private YellowPlayer p3;
    private BluePlayer   p4;
    private WinTracker   winTracker;

    @BeforeEach
    void setUp() {
        p1 = new RedPlayer();
        p2 = new GreenPlayer();
        p3 = new YellowPlayer();
        p4 = new BluePlayer();
        GameEventPublisher publisher = new GameEventPublisher(); // no listeners; events discarded
        winTracker = new WinTracker(List.of(p1, p2, p3, p4), publisher);
    }

    /** Helper: sets all pieces of a player to HOME state. */
    private static void makeAllHome(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            piece.setState(PieceState.HOME);
        }
    }

    // Finishing order
    @Test
    @DisplayName("should_addPlayerToFinishingOrder_when_allPiecesAreHome")
    void should_addPlayerToFinishingOrder_when_allPiecesAreHome() {
        // Arrange
        makeAllHome(p1);

        // Act
        winTracker.checkWinCondition(p1);

        // Assert
        Assertions.assertTrue(winTracker.getFinishingOrder().contains(p1));
    }

    @Test
    @DisplayName("should_notAddPlayer_when_notAllPiecesAreHome")
    void should_notAddPlayer_when_notAllPiecesAreHome() {
        // Arrange Ã¢â‚¬â€ p1 has not finished (default state = BASE)

        // Act
        winTracker.checkWinCondition(p1);

        // Assert
        Assertions.assertFalse(winTracker.getFinishingOrder().contains(p1));
    }

    // Game-over trigger
    @Test
    @DisplayName("should_setGameOver_when_threePlayersHaveFinished")
    void should_setGameOver_when_threePlayersHaveFinished() {
        // Arrange
        makeAllHome(p1);
        makeAllHome(p2);
        makeAllHome(p3);

        winTracker.checkWinCondition(p1);
        winTracker.checkWinCondition(p2);

        // Act
        boolean gameOver = winTracker.checkWinCondition(p3);

        // Assert
        Assertions.assertTrue(gameOver);
        Assertions.assertTrue(winTracker.isGameOver());
    }

    // Fourth-place auto-assignment
    @Test
    @DisplayName("should_autoAssignLastPlace_when_threePlayersFinish")
    void should_autoAssignLastPlace_when_threePlayersFinish() {
        // Arrange
        makeAllHome(p1);
        makeAllHome(p2);
        makeAllHome(p3);

        winTracker.checkWinCondition(p1);
        winTracker.checkWinCondition(p2);
        winTracker.checkWinCondition(p3);

        // Assert Ã¢â‚¬â€ all four players must appear in finishing order
        Assertions.assertEquals(GameConstants.NUM_PLAYERS, winTracker.getFinishingOrder().size());
        Assertions.assertTrue(winTracker.getFinishingOrder().contains(p4));
    }
}

