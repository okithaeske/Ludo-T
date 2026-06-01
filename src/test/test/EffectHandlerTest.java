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

    @BeforeEach
    void setUp() {
        board = new Board();
        publisher = new GameEventPublisher();
        RedPlayer player = new RedPlayer();
        TurnManager turnManager = new TurnManager(List.of(player));
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
}

