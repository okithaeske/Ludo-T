package test;

import engine.TurnManager;
import model.RandomInitiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import player.GreenPlayer;
import player.RedPlayer;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TurnManager")
class TurnManagerTest extends BaseTest {

    private RedPlayer   red;
    private GreenPlayer green;
    private TurnManager tm;

    @BeforeEach
    void setUp() {
        red   = new RedPlayer();
        green = new GreenPlayer();
        tm    = new TurnManager(List.of(red, green));
    }

    // rollDice / consecutive sixes

    @Test
    @DisplayName("should_incrementConsecutiveSixes_when_sixIsRolled")
    void should_incrementConsecutiveSixes_when_sixIsRolled() {
        // Arrange â€” force a 6 by pre-seeding
        forceNextRoll(6);

        // Act
        tm.rollDice(red);

        // Assert
        Assertions.assertEquals(1, tm.getConsecutiveSixes());
    }

    @Test
    @DisplayName("should_resetConsecutiveSixes_when_nonSixIsRolled")
    void should_resetConsecutiveSixes_when_nonSixIsRolled() {
        // Arrange â€” roll a 6, then a non-6
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(3);

        // Act
        tm.rollDice(red);

        // Assert
        Assertions.assertEquals(0, tm.getConsecutiveSixes());
    }

    @Test
    @DisplayName("should_detectTripleSix_when_sixRolledThreeTimes")
    void should_detectTripleSix_when_sixRolledThreeTimes() {
        // Arrange
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(6); tm.rollDice(red);

        // Assert
        Assertions.assertTrue(tm.isTripleSix());
    }

    // advanceToNextPlayer

    @Test
    @DisplayName("should_resetConsecutiveSixes_when_advanceToNextPlayerCalled")
    void should_resetConsecutiveSixes_when_advanceToNextPlayerCalled() {
        // Arrange â€” build up two sixes
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(6); tm.rollDice(red);

        // Act
        tm.advanceToNextPlayer();

        // Assert
        Assertions.assertEquals(0, tm.getConsecutiveSixes());
    }

    @Test
    @DisplayName("should_clearExtraRoll_when_advanceToNextPlayerCalled")
    void should_clearExtraRoll_when_advanceToNextPlayerCalled() {
        // Arrange
        tm.grantExtraRoll();

        // Act
        tm.advanceToNextPlayer();

        // Assert
        Assertions.assertFalse(tm.isExtraRollPending());
    }

    // setOrder / getTurnOrder

    @Test
    @DisplayName("should_reorderPlayers_when_setOrderCalledWithNonFirstPlayer")
    void should_reorderPlayers_when_setOrderCalledWithNonFirstPlayer() {
        // Act â€” make Green go first
        tm.setOrder(green);

        // Assert
        Assertions.assertEquals(green, tm.getTurnOrder().get(0));
        Assertions.assertEquals(red,   tm.getTurnOrder().get(1));
    }

    @Test
    @DisplayName("should_keepOrder_when_setOrderCalledWithAlreadyFirstPlayer")
    void should_keepOrder_when_setOrderCalledWithAlreadyFirstPlayer() {
        // Act â€” Red is already first
        tm.setOrder(red);

        // Assert
        Assertions.assertEquals(red,   tm.getTurnOrder().get(0));
        Assertions.assertEquals(green, tm.getTurnOrder().get(1));
    }

    // Triple three (frozen escape)

    @Test
    @DisplayName("should_detectTripleThree_when_threeRolledThreeTimes")
    void should_detectTripleThree_when_threeRolledThreeTimes() {
        // Arrange â€” roll 3 three times for red
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);

        // Assert
        Assertions.assertTrue(tm.isTripleThreeForPlayer(red));
    }

    @Test
    @DisplayName("should_resetThreeCount_when_nonThreeRolled")
    void should_resetThreeCount_when_nonThreeRolled() {
        // Arrange
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(5);

        // Act
        tm.rollDice(red);

        // Assert
        Assertions.assertFalse(tm.isTripleThreeForPlayer(red));
    }

    @Test
    @DisplayName("should_notShareThreeCount_when_differentPlayerRolls")
    void should_notShareThreeCount_when_differentPlayerRolls() {
        // Arrange â€” three 3s for red, one 3 for green
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(green);

        // Assert â€” green has only one 3, not triple
        Assertions.assertFalse(tm.isTripleThreeForPlayer(green));
    }

    // extra roll

    @Test
    @DisplayName("should_setPending_when_grantExtraRollCalled")
    void should_setPending_when_grantExtraRollCalled() {
        tm.grantExtraRoll();
        Assertions.assertTrue(tm.isExtraRollPending());
    }

    @Test
    @DisplayName("should_clearPending_when_clearExtraRollCalled")
    void should_clearPending_when_clearExtraRollCalled() {
        tm.grantExtraRoll();
        tm.clearExtraRoll();
        Assertions.assertFalse(tm.isExtraRollPending());
    }

    // resetRollStreaks

    @Test
    @DisplayName("should_clearAllStreaks_when_resetRollStreaksCalled")
    void should_clearAllStreaks_when_resetRollStreaksCalled() {
        // Arrange â€” build up some state
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(3); tm.rollDice(red);
        tm.grantExtraRoll();

        // Act
        tm.resetRollStreaks();

        // Assert
        Assertions.assertEquals(0, tm.getConsecutiveSixes());
        Assertions.assertFalse(tm.isExtraRollPending());
        Assertions.assertFalse(tm.isTripleThreeForPlayer(red));
    }

    // handleTripleSix

    @Test
    @DisplayName("should_resetSixes_when_handleTripleSixCalled")
    void should_resetSixes_when_handleTripleSixCalled() {
        // Arrange â€” reach triple six
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(6); tm.rollDice(red);
        forceNextRoll(6); tm.rollDice(red);
        Assertions.assertTrue(tm.isTripleSix());

        // Act
        tm.handleTripleSix();

        // Assert
        Assertions.assertEquals(0, tm.getConsecutiveSixes());
        Assertions.assertFalse(tm.isExtraRollPending());
    }

    // Triple six: bonus rolls are voided (Rule 4)
    @Test
    @DisplayName("should_ignoreThirdConsecutiveSix_when_threeRolledInRow")
    void should_ignoreThirdConsecutiveSix_when_threeRolledInRow() {
        // Arrange â€” two sixes grant two pending extra rolls; the third triggers a penalty.
        // handleTripleSix() must clear the pending state so no extra roll remains.
        forceNextRoll(6); tm.rollDice(red); tm.grantExtraRoll();
        forceNextRoll(6); tm.rollDice(red); tm.grantExtraRoll();
        forceNextRoll(6); tm.rollDice(red);
        Assertions.assertTrue(tm.isTripleSix());

        // Act
        tm.handleTripleSix();

        // Assert â€” extra-roll flag is cleared; the three sixes grant nothing
        Assertions.assertFalse(tm.isExtraRollPending());
        Assertions.assertEquals(0, tm.getConsecutiveSixes());
    }

    /**
     * Seeds the RNG so the next {@code nextInt(6)} returns {@code (desiredRoll - 1)},
     * which the dice translates to {@code desiredRoll}.
     */
    private void forceNextRoll(int desiredRoll) {
        // Brute-force: try seeds until the first nextInt(6) produces the target.
        for (long seed = 0; seed < 10_000; seed++) {
            RandomInitiator.getInstance().setSeed(seed);
            if (RandomInitiator.getInstance().nextInt(6) + 1 == desiredRoll) {
                RandomInitiator.getInstance().setSeed(seed);
                return;
            }
        }
        Assertions.fail("Could not find a seed to produce roll " + desiredRoll);
    }
}
