package test;

import engine.EffectHandler;
import engine.RuleEngine;
import engine.TurnExecutor;
import engine.TurnManager;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.Piece;
import model.RandomInitiator;
import player.GreenPlayer;
import player.RedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TurnExecutor")
class TurnExecutorTest extends BaseTest {

    private static final GameEventListener SILENT = new GameEventListener() { };

    private Board board;
    private RuleEngine ruleEngine;
    private TurnManager turnManager;
    private TurnExecutor executor;
    private RedPlayer red;
    private GreenPlayer green;

    @BeforeEach
    void setUp() {
        board     = new Board();
        red       = new RedPlayer();
        green     = new GreenPlayer();
        List<player.AbstractPlayer> players = List.of(red, green);
        turnManager   = new TurnManager(players);
        GameEventPublisher publisher = new GameEventPublisher();
        publisher.addListener(SILENT);
        ruleEngine    = new RuleEngine(board, GameMode.LUDO_T);
        EffectHandler effectHandler = new EffectHandler(
                board, turnManager, publisher, ruleEngine, players);
        executor = new TurnExecutor(
                board, ruleEngine, turnManager, publisher,
                effectHandler, GameMode.LUDO_T, players);
    }

    // Extra roll on roll-6 (Rule 4)
    @Test
    @DisplayName("should_grantExtraRoll_when_sixIsRolled")
    void should_grantExtraRoll_when_sixIsRolled() {
        // Arrange â€” active Red piece at 10; rolling 6 moves it and triggers extra roll.
        Piece r1 = red.getPieces()[0];
        r1.setState(PieceState.ACTIVE);
        r1.moveTo(10);
        r1.assignInitialDirection(Direction.CW);
        board.placePiece(r1, 10);

        forceNextRoll(6);

        // Act
        executor.executeTurn(red, false);

        // Assert
        Assertions.assertTrue(turnManager.isExtraRollPending());
    }

    // Capture grants bonus roll (Rule T-2)
    @Test
    @DisplayName("should_grantBonusRoll_when_captureOccurs")
    void should_grantBonusRoll_when_captureOccurs() {
        // Arrange â€” Red at 10, lone Green at 11. Roll 1 â†’ Red captures Green.
        Piece r1 = red.getPieces()[0];
        r1.setState(PieceState.ACTIVE);
        r1.moveTo(10);
        r1.assignInitialDirection(Direction.CW);
        board.placePiece(r1, 10);

        Piece g1 = green.getPieces()[0];
        g1.setState(PieceState.ACTIVE);
        g1.moveTo(11);
        g1.assignInitialDirection(Direction.CW);
        board.placePiece(g1, 11);

        forceNextRoll(1);

        // Act
        executor.executeTurn(red, false);

        // Assert
        Assertions.assertTrue(turnManager.isExtraRollPending());
    }

    // Captured piece returned to base (Rule 6)
    @Test
    @DisplayName("should_returnOpponentPieceToBase_when_pieceLandsOnOccupiedCell")
    void should_returnOpponentPieceToBase_when_pieceLandsOnOccupiedCell() {
        // Arrange â€” Red at 10, lone Green at 11. Roll 1 â†’ Red captures Green.
        Piece r1 = red.getPieces()[0];
        r1.setState(PieceState.ACTIVE);
        r1.moveTo(10);
        r1.assignInitialDirection(Direction.CW);
        board.placePiece(r1, 10);

        Piece g1 = green.getPieces()[0];
        g1.setState(PieceState.ACTIVE);
        g1.moveTo(11);
        g1.assignInitialDirection(Direction.CW);
        board.placePiece(g1, 11);

        forceNextRoll(1);

        // Act
        executor.executeTurn(red, false);

        // Assert â€” captured piece fully reset to base
        Assertions.assertEquals(PieceState.BASE, g1.getState());
        Assertions.assertEquals(GameConstants.NO_POSITION, g1.getPosition());
        Assertions.assertFalse(board.getPiecesAt(11).contains(g1));
    }

    /** Brute-forces a seed so the next dice roll equals {@code desiredRoll}. */
    private void forceNextRoll(int desiredRoll) {
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
