package test;

import model.Dice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dice")
class DiceTest extends BaseTest {

    @Test
    @DisplayName("should_alwaysReturnOneToSix_when_rolledRepeatedly")
    void should_alwaysReturnOneToSix_when_rolledRepeatedly() {
        // Arrange
        Dice dice = Dice.getInstance();

        // Act + Assert
        for (int i = 0; i < 1000; i++) {
            int roll = dice.roll();
            Assertions.assertTrue(roll >= 1 && roll <= 6,
                    "Roll " + roll + " is outside the valid range [1, 6]");
        }
    }
}

