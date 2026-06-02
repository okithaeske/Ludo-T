package test;

import enums.Colour;
import enums.Direction;
import enums.PieceEffect;
import enums.PieceState;
import model.GameConstants;
import model.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Piece")
class PieceTest extends BaseTest {

    private Piece piece;

    @BeforeEach
    void setUp() {
        piece = new Piece("R1", Colour.RED);
    }

    // Default state
    @Test
    @DisplayName("should_startAtBasePosition_when_newlyCreated")
    void should_startAtBasePosition_when_newlyCreated() {
        // Assert â€” BASE_POSITION was merged into NO_POSITION; both equal -1.
        // PieceState.BASE distinguishes "at base" from "at home" at the domain level.
        Assertions.assertEquals(GameConstants.NO_POSITION, piece.getPosition());
    }

    @Test
    @DisplayName("should_haveBaseState_when_newlyCreated")
    void should_haveBaseState_when_newlyCreated() {
        // Assert
        Assertions.assertEquals(PieceState.BASE, piece.getState());
    }

    @Test
    @DisplayName("should_haveClockwiseDirection_when_newlyCreated")
    void should_haveClockwiseDirection_when_newlyCreated() {
        // Assert
        Assertions.assertEquals(Direction.CW, piece.getDirection());
    }

    @Test
    @DisplayName("should_haveZeroCaptureCount_when_newlyCreated")
    void should_haveZeroCaptureCount_when_newlyCreated() {
        // Assert
        Assertions.assertEquals(0, piece.getCaptureCount());
    }

    @Test
    @DisplayName("should_haveNoEffect_when_newlyCreated")
    void should_haveNoEffect_when_newlyCreated() {
        // Assert
        Assertions.assertEquals(PieceEffect.NONE, piece.getActiveEffect());
    }

    @Test
    @DisplayName("should_haveZeroApproachPassCount_when_newlyCreated")
    void should_haveZeroApproachPassCount_when_newlyCreated() {
        // Assert
        Assertions.assertEquals(0, piece.getApproachPassCount());
    }

    // reset()
    @Test
    @DisplayName("should_resetPositionToBase_when_resetCalled")
    void should_resetPositionToBase_when_resetCalled() {
        // Arrange
        piece.moveTo(25);
        piece.setState(PieceState.ACTIVE);

        // Act
        piece.reset();

        // Assert
        Assertions.assertEquals(GameConstants.NO_POSITION, piece.getPosition());
    }

    @Test
    @DisplayName("should_resetStateToBase_when_resetCalled")
    void should_resetStateToBase_when_resetCalled() {
        // Arrange
        piece.setState(PieceState.ACTIVE);

        // Act
        piece.reset();

        // Assert
        Assertions.assertEquals(PieceState.BASE, piece.getState());
    }

    @Test
    @DisplayName("should_clearActiveEffect_when_resetCalled")
    void should_clearActiveEffect_when_resetCalled() {
        // Arrange
        piece.applyEffect(PieceEffect.FROZEN);

        // Act
        piece.reset();

        // Assert
        Assertions.assertEquals(PieceEffect.NONE, piece.getActiveEffect());
    }

    @Test
    @DisplayName("should_resetApproachPassCount_when_resetCalled")
    void should_resetApproachPassCount_when_resetCalled() {
        // Arrange
        piece.incrementApproachPass();
        piece.incrementApproachPass();

        // Act
        piece.reset();

        // Assert
        Assertions.assertEquals(0, piece.getApproachPassCount());
    }

    @Test
    @DisplayName("should_resetDirectionToCW_when_resetCalled")
    void should_resetDirectionToCW_when_resetCalled() {
        // Arrange
        piece.setDirection(Direction.CCW);

        // Act
        piece.reset();

        // Assert
        Assertions.assertEquals(Direction.CW, piece.getDirection());
    }

    // canEnterHomeStraight()
    @Test
    @DisplayName("should_returnFalse_when_captureCountIsZero")
    void should_returnFalse_when_captureCountIsZero() {
        // Assert
        Assertions.assertFalse(piece.canEnterHomeStraight());
    }

    @Test
    @DisplayName("should_returnTrue_when_captureCountIsAtLeastOne")
    void should_returnTrue_when_captureCountIsAtLeastOne() {
        // Arrange
        piece.capture();

        // Assert
        Assertions.assertTrue(piece.canEnterHomeStraight());
    }

    // Full capture reset (Rule T-9)
    @Test
    @DisplayName("should_resetAllPieceInfo_when_pieceIsCaptured")
    void should_resetAllPieceInfo_when_pieceIsCaptured() {
        // Arrange â€” piece in the middle of a game: active on board with effects and history.
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(25);
        piece.assignInitialDirection(Direction.CCW);
        piece.capture();
        piece.applyEffect(PieceEffect.FROZEN);
        piece.incrementApproachPass();
        piece.setHomeStraightPosition(2);

        // Act â€” reset() is called by TurnExecutor when the piece is captured
        piece.reset();

        // Assert â€” every field must be back to its newly-constructed default
        Assertions.assertEquals(GameConstants.NO_POSITION, piece.getPosition());
        Assertions.assertEquals(PieceState.BASE, piece.getState());
        Assertions.assertEquals(Direction.CW, piece.getDirection());
        Assertions.assertEquals(Direction.CW, piece.getOriginalDirection());
        Assertions.assertEquals(0, piece.getCaptureCount());
        Assertions.assertEquals(PieceEffect.NONE, piece.getActiveEffect());
        Assertions.assertEquals(0, piece.getEffectRoundsLeft());
        Assertions.assertEquals(0, piece.getApproachPassCount());
        Assertions.assertEquals(0, piece.getHomeStraightPosition());
    }

    // getEffectiveRoll()
    @Test
    @DisplayName("should_doubleRoll_when_pieceIsEnergised")
    void should_doubleRoll_when_pieceIsEnergised() {
        // Arrange
        piece.applyEffect(PieceEffect.ENERGISED);

        // Act
        int effective = piece.getEffectiveRoll(3);

        // Assert
        Assertions.assertEquals(6, effective);
    }

    @Test
    @DisplayName("should_halveRoll_when_pieceIsSick")
    void should_halveRoll_when_pieceIsSick() {
        // Arrange
        piece.applyEffect(PieceEffect.SICK);

        // Act
        int effective = piece.getEffectiveRoll(4);

        // Assert
        Assertions.assertEquals(2, effective);
    }

    @Test
    @DisplayName("should_returnRollUnchanged_when_noEffectActive")
    void should_returnRollUnchanged_when_noEffectActive() {
        // Act
        int effective = piece.getEffectiveRoll(5);

        // Assert
        Assertions.assertEquals(5, effective);
    }

    // tickEffectCountdown()
    @Test
    @DisplayName("should_decrementRoundsLeft_when_effectIsTicked")
    void should_decrementRoundsLeft_when_effectIsTicked() {
        // Arrange
        piece.applyEffect(PieceEffect.FROZEN); // sets effectRoundsLeft = EFFECT_DURATION (4)

        // Act
        piece.tickEffectCountdown();

        // Assert
        Assertions.assertEquals(GameConstants.EFFECT_DURATION - 1, piece.getEffectRoundsLeft());
    }

    @Test
    @DisplayName("should_clearEffect_when_countdownReachesZero")
    void should_clearEffect_when_countdownReachesZero() {
        // Arrange
        piece.applyEffect(PieceEffect.FROZEN);

        // Act
        for (int i = 0; i < GameConstants.EFFECT_DURATION; i++) {
            piece.tickEffectCountdown();
        }

        // Assert
        Assertions.assertEquals(PieceEffect.NONE, piece.getActiveEffect());
    }

    @Test
    @DisplayName("should_notTickBelowZero_when_noEffectActive")
    void should_notTickBelowZero_when_noEffectActive() {
        // Arrange Ã¢â‚¬â€ piece has no effect

        // Act
        piece.tickEffectCountdown();

        // Assert
        Assertions.assertEquals(0, piece.getEffectRoundsLeft());
    }
}

