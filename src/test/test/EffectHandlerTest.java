package test.test;

import engine.EffectHandler;
import engine.RuleEngine;
import engine.TurnManager;
import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceEffect;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;
import model.RandomInitiator;
import player.RedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EffectHandler")
class EffectHandlerTest extends BaseTest {

    private Board board;
    private EffectHandler effectHandler;
    private GameEventPublisher publisher;
    private RedPlayer player;
    private TurnManager turnManager;

    @BeforeEach
    void setUp() {
        board = new Board();
        publisher = new GameEventPublisher();
        player = new RedPlayer();
        turnManager = new TurnManager(List.of(player));
        RuleEngine ruleEngine = new RuleEngine(board, GameMode.LUDO_T);
        effectHandler = new EffectHandler(board, turnManager, publisher, ruleEngine, List.of(player));
    }

    //Coin toss
    @Test
    @DisplayName("should_setDirectionAndOriginalDirection_when_coinTossPerformed")
    void should_setDirectionAndOriginalDirection_when_coinTossPerformed() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(board.getStartX(Colour.RED));

        // Act
        effectHandler.performCoinToss(piece);

        // Assert both fields must be set to the same value
        assertEquals(piece.getDirection(), piece.getOriginalDirection());
    }

    @Test
    @DisplayName("should_assignCWDirection_when_coinTossReturnsZero")
    void should_assignCWDirection_when_coinTossReturnsZero() {
        // Arrange use seed 42; determine what the first nextInt(2) returns,
        // then reset the seed so performCoinToss sees the same sequence.
        RandomInitiator.getInstance().setSeed(42L);
        int toss = RandomInitiator.getInstance().nextInt(2);
        Direction expected = (toss == 0) ? Direction.CW : Direction.CCW;

        RandomInitiator.getInstance().setSeed(42L); // reset for the actual call
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(board.getStartX(Colour.RED));

        // Act
        effectHandler.performCoinToss(piece);

        // Assert
        assertEquals(expected, piece.getDirection());
    }

    // GAMMA teleport with CW piece
    @Test
    @DisplayName("should_flipDirectionToCCW_when_CWPieceTeleportsToGamma")
    void should_flipDirectionToCCW_when_CWPieceTeleportsToGamma() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(10);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.GAMMA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert
        assertEquals(Direction.CCW, piece.getDirection());
    }

    @Test
    @DisplayName("should_landOnGammaCell_when_CWPieceTeleportsToGamma")
    void should_landOnGammaCell_when_CWPieceTeleportsToGamma() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(10);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.GAMMA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert
        assertEquals(GameConstants.GAMMA_CELL, piece.getPosition());
    }

    // GAMMA teleport with CCW piece redirect to BETA
    @Test
    @DisplayName("should_redirectToBeta_when_CCWPieceTeleportsToGamma")
    void should_redirectToBeta_when_CCWPieceTeleportsToGamma() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(10);
        piece.assignInitialDirection(Direction.CCW);
        board.placePiece(piece, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.GAMMA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert redirected to BETA, so piece is at BETA_CELL
        assertEquals(GameConstants.BETA_CELL, piece.getPosition());
    }

    @Test
    @DisplayName("should_applyFrozenEffect_when_CCWPieceTeleportsToGamma")
    void should_applyFrozenEffect_when_CCWPieceTeleportsToGamma() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(10);
        piece.assignInitialDirection(Direction.CCW);
        board.placePiece(piece, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.GAMMA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert â€” BETA applies FROZEN
        assertEquals(PieceEffect.FROZEN, piece.getActiveEffect());
    }

    // Teleport onto full friendly block → piece sent to base
    @Test
    @DisplayName("should_returnToBase_when_teleportLandsOnFullFriendlyBlock")
    void should_returnToBase_when_teleportLandsOnFullFriendlyBlock() {
        // Arrange — two friendly Red pieces already form a block at BETA_CELL.
        Piece blocker1 = new Piece("R2", Colour.RED);
        Piece blocker2 = new Piece("R3", Colour.RED);
        blocker1.setState(PieceState.ACTIVE);
        blocker2.setState(PieceState.ACTIVE);
        blocker1.moveTo(GameConstants.BETA_CELL);
        blocker2.moveTo(GameConstants.BETA_CELL);
        board.placePiece(blocker1, GameConstants.BETA_CELL);
        board.placePiece(blocker2, GameConstants.BETA_CELL);

        // Teleporting piece aimed at BETA
        Piece piece = buildActivePieceAt(Direction.CW, 10);
        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.BETA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert — cannot join the full block; piece returned to base
        assertEquals(PieceState.BASE, piece.getState());
    }

    // Alpha teleport — ENERGISED (Rule T-12)
    @Test
    @DisplayName("should_energisePiece_when_teleportedToAlpha")
    void should_energisePiece_when_teleportedToAlpha() {
        // Arrange — force seed so resolveAlphaEffect() returns ENERGISED (nextInt(2) = 0).
        forceSeedForAlpha(0);
        Piece piece = buildActivePieceAt(Direction.CW, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.ALPHA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert
        assertEquals(PieceEffect.ENERGISED, piece.getActiveEffect());
        assertEquals(GameConstants.ALPHA_CELL, piece.getPosition());
    }

    // Alpha teleport — SICK (Rule T-12)
    @Test
    @DisplayName("should_sickenPiece_when_teleportedToAlpha")
    void should_sickenPiece_when_teleportedToAlpha() {
        // Arrange — force seed so resolveAlphaEffect() returns SICK (nextInt(2) = 1).
        forceSeedForAlpha(1);
        Piece piece = buildActivePieceAt(Direction.CW, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.ALPHA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert
        assertEquals(PieceEffect.SICK, piece.getActiveEffect());
    }

    // Beta teleport — FROZEN for four rounds (Rule T-13)
    @Test
    @DisplayName("should_freezePieceForFourRounds_when_teleportedToBeta")
    void should_freezePieceForFourRounds_when_teleportedToBeta() {
        // Arrange
        Piece piece = buildActivePieceAt(Direction.CW, 10);

        MoveResult result = new MoveResult();
        result.setTeleportDest(TeleportDest.BETA);

        // Act
        effectHandler.handleTeleport(piece, result);

        // Assert
        assertEquals(PieceEffect.FROZEN, piece.getActiveEffect());
        assertEquals(GameConstants.EFFECT_DURATION, piece.getEffectRoundsLeft());
        assertEquals(GameConstants.BETA_CELL, piece.getPosition());
    }

    // Frozen escape on triple three (Rule T-13)
    @Test
    @DisplayName("should_teleportToBase_when_frozenPieceRollsThreeConsecutively")
    void should_teleportToBase_when_frozenPieceRollsThreeConsecutively() {
        // Arrange — freeze a piece, then simulate three consecutive 3-rolls for the player.
        Piece frozen = player.getPieces()[0];
        frozen.applyEffect(PieceEffect.FROZEN);
        frozen.setState(PieceState.ACTIVE);
        frozen.moveTo(10);
        board.placePiece(frozen, 10);

        // Roll 3 three times for the player using brute-force seed search.
        for (int i = 0; i < 3; i++) {
            forceRoll(3);
            turnManager.rollDice(player);
        }
        assertTrue(effectHandler.isFrozenEscape(player));

        // Act
        effectHandler.handleFrozenEscape(player);

        // Assert — piece returned to base
        assertEquals(GameConstants.NO_POSITION, frozen.getPosition());
        assertEquals(PieceState.BASE, frozen.getState());
    }

    // Frozen piece tick

    @Test
    @DisplayName("should_decrementFrozenCountdown_when_frozenPieceIsTicked")
    void should_decrementFrozenCountdown_when_frozenPieceIsTicked() {
        // Arrange
        RedPlayer player = new RedPlayer();
        Piece frozenPiece = player.getPieces()[0];
        frozenPiece.applyEffect(PieceEffect.FROZEN); // effectRoundsLeft = EFFECT_DURATION

        int before = frozenPiece.getEffectRoundsLeft();

        // Act
        effectHandler.tickFrozenPiece(player);

        // Assert
        assertEquals(before - 1, frozenPiece.getEffectRoundsLeft());
    }

    // Helpers

    private Piece buildActivePieceAt(Direction dir, int position) {
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(dir);
        board.placePiece(piece, position);
        return piece;
    }

    /** Forces the RNG so the next nextInt(2) returns {@code target} (0 or 1). */
    private void forceSeedForAlpha(int target) {
        for (long seed = 0; seed < 10_000; seed++) {
            RandomInitiator.getInstance().setSeed(seed);
            if (RandomInitiator.getInstance().nextInt(2) == target) {
                RandomInitiator.getInstance().setSeed(seed);
                return;
            }
        }
        fail("Could not find a seed for alpha target " + target);
    }

    /** Forces the RNG so the next dice roll equals {@code desiredRoll}. */
    private void forceRoll(int desiredRoll) {
        for (long seed = 0; seed < 10_000; seed++) {
            RandomInitiator.getInstance().setSeed(seed);
            if (RandomInitiator.getInstance().nextInt(6) + 1 == desiredRoll) {
                RandomInitiator.getInstance().setSeed(seed);
                return;
            }
        }
        fail("Could not find a seed for roll " + desiredRoll);
    }
}

