package test.test;

import engine.FirstPlayerSelector;
import engine.TurnManager;
import logger.GameEventPublisher;
import model.RandomInitiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import player.AbstractPlayer;
import player.BluePlayer;
import player.GreenPlayer;
import player.RedPlayer;
import player.YellowPlayer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FirstPlayerSelector")
class FirstPlayerSelectorTest extends BaseTest {

    private RedPlayer    red;
    private GreenPlayer  green;
    private YellowPlayer yellow;
    private BluePlayer   blue;

    private TurnManager          turnManager;
    private GameEventPublisher   publisher;

    @BeforeEach
    void setUp() {
        red    = new RedPlayer();
        green  = new GreenPlayer();
        yellow = new YellowPlayer();
        blue   = new BluePlayer();
        turnManager = new TurnManager(List.of(red, green, yellow, blue));
        publisher   = new GameEventPublisher(); // no listeners — events discarded
    }

    @Test
    @DisplayName("should_returnOnePlayer_when_selectFirstPlayerCalled")
    void should_returnOnePlayer_when_selectFirstPlayerCalled() {
        // Arrange
        FirstPlayerSelector selector =
                new FirstPlayerSelector(List.of(red, green, yellow, blue), turnManager, publisher);

        // Act
        AbstractPlayer first = selector.selectFirstPlayer();

        // Assert — must be one of the four players
        assertTrue(first == red || first == green || first == yellow || first == blue);
    }

    @Test
    @DisplayName("should_returnSamePlayerForSameSeed_when_noTie")
    void should_returnSamePlayerForSameSeed_when_noTie() {
        // Arrange — run twice with the same seed; must get the same winner
        RandomInitiator.getInstance().setSeed(42L);
        AbstractPlayer first1 = new FirstPlayerSelector(
                List.of(red, green, yellow, blue), turnManager, publisher)
                .selectFirstPlayer();

        RandomInitiator.getInstance().setSeed(42L);
        AbstractPlayer first2 = new FirstPlayerSelector(
                List.of(red, green, yellow, blue), turnManager, publisher)
                .selectFirstPlayer();

        // Assert
        assertSame(first1, first2);
    }

    @Test
    @DisplayName("should_breakTie_when_twoPlayersRollSame")
    void should_breakTie_when_twoPlayersRollSame() {
        // Arrange — two-player scenario so a tie is more likely
        TurnManager tm2    = new TurnManager(List.of(red, green));
        FirstPlayerSelector selector =
                new FirstPlayerSelector(List.of(red, green), tm2, publisher);

        // Act
        AbstractPlayer winner = selector.selectFirstPlayer();

        // Assert — exactly one winner, must be red or green
        assertNotNull(winner);
        assertTrue(winner == red || winner == green);
    }

    @Test
    @DisplayName("should_returnSinglePlayer_when_onlyOneCandidate")
    void should_returnSinglePlayer_when_onlyOneCandidate() {
        // Arrange
        TurnManager single = new TurnManager(List.of(red));
        FirstPlayerSelector selector =
                new FirstPlayerSelector(List.of(red), single, publisher);

        // Act
        AbstractPlayer winner = selector.selectFirstPlayer();

        // Assert
        assertSame(red, winner);
    }
}
