package test;

import enums.Colour;
import player.AbstractPlayer;
import player.BluePlayer;
import player.GreenPlayer;
import player.PlayerFactory;
import player.RedPlayer;
import player.YellowPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerFactory")
class PlayerFactoryTest extends BaseTest {

    @Test
    @DisplayName("should_returnRedPlayer_when_colourIsRed")
    void should_returnRedPlayer_when_colourIsRed() {
        // Act
        AbstractPlayer player = PlayerFactory.createPlayer(Colour.RED);

        // Assert
        Assertions.assertInstanceOf(RedPlayer.class, player);
    }

    @Test
    @DisplayName("should_returnGreenPlayer_when_colourIsGreen")
    void should_returnGreenPlayer_when_colourIsGreen() {
        // Act
        AbstractPlayer player = PlayerFactory.createPlayer(Colour.GREEN);

        // Assert
        Assertions.assertInstanceOf(GreenPlayer.class, player);
    }

    @Test
    @DisplayName("should_returnYellowPlayer_when_colourIsYellow")
    void should_returnYellowPlayer_when_colourIsYellow() {
        // Act
        AbstractPlayer player = PlayerFactory.createPlayer(Colour.YELLOW);

        // Assert
        Assertions.assertInstanceOf(YellowPlayer.class, player);
    }

    @Test
    @DisplayName("should_returnBluePlayer_when_colourIsBlue")
    void should_returnBluePlayer_when_colourIsBlue() {
        // Act
        AbstractPlayer player = PlayerFactory.createPlayer(Colour.BLUE);

        // Assert
        Assertions.assertInstanceOf(BluePlayer.class, player);
    }

    @Test
    @DisplayName("should_throwIllegalArgumentException_when_colourIsNone")
    void should_throwIllegalArgumentException_when_colourIsNone() {
        // Act + Assert
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> PlayerFactory.createPlayer(Colour.NONE));
    }
}

