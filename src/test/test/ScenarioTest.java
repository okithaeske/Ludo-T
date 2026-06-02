package test.test;

import engine.EffectHandler;
import engine.RuleEngine;
import engine.TurnExecutor;
import engine.TurnManager;
import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.MysteryCell;
import model.Piece;
import model.RandomInitiator;
import player.AbstractPlayer;
import player.GreenPlayer;
import player.RedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario tests — verify that the full pipeline
 * (roll → strategy → validation → apply → board state)
 * produces the correct observable board state after a complete turn.
 *
 * Each test sets up a specific game position, forces a deterministic dice
 * roll via seed search, executes a full turn through TurnExecutor, and then
 * inspects the Board and Piece state directly.
 */
@DisplayName("Scenario (full-pipeline board-state)")
class ScenarioTest extends BaseTest {

    private Board board;
    private TurnManager turnManager;
    private GameEventPublisher publisher;
    private TurnExecutor executor;
    private RedPlayer red;
    private GreenPlayer green;

    @BeforeEach
    void setUp() {
        board     = new Board();
        red       = new RedPlayer();
        green     = new GreenPlayer();
        List<AbstractPlayer> players = List.of(red, green);
        turnManager = new TurnManager(players);
        publisher   = new GameEventPublisher();
        publisher.addListener(new GameEventListener() { }); // silent — suppresses output
        RuleEngine ruleEngine = new RuleEngine(board, GameMode.LUDO_T);
        EffectHandler effectHandler = new EffectHandler(
                board, turnManager, publisher, ruleEngine, players);
        executor = new TurnExecutor(
                board, ruleEngine, turnManager, publisher,
                effectHandler, GameMode.LUDO_T, players);
    }

    // ── Scenario 1: standard movement ────────────────────────────────────────

    @Test
    @DisplayName("should_updateBoardPosition_when_pieceMovesOnStandardPath")
    void should_updateBoardPosition_when_pieceMovesOnStandardPath() {
        // Arrange — Red piece at 10 (CW), roll 3 → should land at 13.
        Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
        forceNextRoll(3);

        // Act
        executor.executeTurn(red, false);

        // Assert — piece is at 13, old cell is vacated
        assertEquals(13, r1.getPosition());
        assertTrue(board.getPiecesAt(13).contains(r1));
        assertTrue(board.getPiecesAt(10).isEmpty());
    }

    // ── Scenario 2: capture ───────────────────────────────────────────────────

    @Test
    @DisplayName("should_placeAttackerAtCaptureCell_and_returnVictimToBase_when_captureOccurs")
    void should_placeAttackerAtCaptureCell_and_returnVictimToBase_when_captureOccurs() {
        // Arrange — Red at 10, lone Green at 11. Roll 1 → Red captures Green.
        Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
        Piece g1 = activateGreenPieceAt(0, 11, Direction.CW);
        forceNextRoll(1);

        // Act
        executor.executeTurn(red, false);

        // Assert — Red now occupies cell 11; Green is fully reset to base.
        assertEquals(11, r1.getPosition());
        assertTrue(board.getPiecesAt(11).contains(r1));

        assertEquals(GameConstants.NO_POSITION, g1.getPosition());
        assertEquals(PieceState.BASE, g1.getState());
        assertFalse(board.getPiecesAt(11).contains(g1));
    }

    // ── Scenario 3: block movement ────────────────────────────────────────────

    @Test
    @DisplayName("should_moveBothPiecesTogether_when_blockAdvances")
    void should_moveBothPiecesTogether_when_blockAdvances() {
        // Arrange — two Red pieces form a block at position 10.
        // Roll 4: block steps = 4 / 2 = 2 → both pieces land at 12.
        Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
        Piece r2 = activateRedPieceAt(1, 10, Direction.CW);
        forceNextRoll(4);

        // Act
        executor.executeTurn(red, false);

        // Assert — both pieces moved to 12; cell 10 is now empty.
        assertEquals(12, r1.getPosition());
        assertEquals(12, r2.getPosition());
        assertTrue(board.getPiecesAt(12).contains(r1));
        assertTrue(board.getPiecesAt(12).contains(r2));
        assertTrue(board.getPiecesAt(10).isEmpty());
    }

    // ── Scenario 4: base exit ─────────────────────────────────────────────────

    @Test
    @DisplayName("should_placePieceAtStartCell_when_exitingBase")
    void should_placePieceAtStartCell_when_exitingBase() {
        // Arrange — all Red pieces at base; roll 6 brings one piece to RED_START.
        forceNextRoll(6);

        // Act
        executor.executeTurn(red, false);

        // Assert — exactly one Red piece is on the board at RED_START.
        List<Piece> atStart = board.getPiecesAt(GameConstants.RED_START);
        assertEquals(1, atStart.size());
        assertEquals(PieceState.ACTIVE, atStart.get(0).getState());
        assertEquals(Colour.RED, atStart.get(0).getColour());
    }

    // ── Scenario 5: home-straight entry ──────────────────────────────────────

    @Test
    @DisplayName("should_enterHomeStraightAtCorrectPosition_when_piecePassesApproachCell")
    void should_enterHomeStraightAtCorrectPosition_when_piecePassesApproachCell() {
        // Arrange — Red piece at 22 (CW); RED_APPROACH = 24.
        // distToApproach = (24 - 22 + 52) % 52 = 2.
        // Roll 3 → steps = 3 > 2 → enters home straight at position 1 (3 - 2 = 1).
        // Piece needs 1 capture to satisfy canEnterHome in Ludo-T.
        Piece r1 = activateRedPieceAt(0, 22, Direction.CW);
        r1.capture();
        forceNextRoll(3);

        // Act
        executor.executeTurn(red, false);

        // Assert — piece is in the home straight, not on the standard board.
        assertTrue(r1.isInHomeStraight());
        assertEquals(1, r1.getHomeStraightPosition());
        assertEquals(GameConstants.NO_POSITION, r1.getPosition());
        assertTrue(board.getPiecesAt(22).isEmpty());
    }

    // ── Scenario 6: mystery cell teleport ────────────────────────────────────

    @Test
    @DisplayName("should_teleportPieceAwayFromMysteryCell_when_mysteryTriggers")
    void should_teleportPieceAwayFromMysteryCell_when_mysteryTriggers() {
        // Arrange — spawn the mystery cell (requires a piece on the board), record its
        // position, then place Red one CW step before it so a roll of 1 lands on it.
        // The onTeleport event is captured to confirm the teleport pipeline fired,
        // because the teleport destination could coincidentally equal mysteryPos
        // (e.g. mystery at ALPHA_CELL and destination = ALPHA), making a position
        // assertion unreliable.
        AtomicBoolean teleportFired = new AtomicBoolean(false);
        publisher.addListener(new GameEventListener() {
            @Override
            public void onTeleport(Piece piece, TeleportDest dest, int targetCell) {
                teleportFired.set(true);
            }
        });

        Piece anchor = new Piece("D1", Colour.GREEN);
        anchor.setState(PieceState.ACTIVE);
        anchor.moveTo(0);
        board.placePiece(anchor, 0);

        MysteryCell mystery = new MysteryCell(GameConstants.NO_POSITION);
        mystery.spawn(board);   // RNG consumed here; result is deterministic from seed 42
        board.setMysteryCell(mystery);
        int mysteryPos = mystery.getPosition();

        board.removePiece(anchor, 0);

        int startPos = (mysteryPos - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        Piece r1 = activateRedPieceAt(0, startPos, Direction.CW);

        forceNextRoll(1);

        // Act
        executor.executeTurn(red, false);

        // Assert — piece left its start position and the teleport event was published.
        assertFalse(board.getPiecesAt(startPos).contains(r1),
                "Piece should not remain at pre-teleport position");
        assertTrue(teleportFired.get(),
                "onTeleport must fire when a piece lands on the mystery cell");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Piece activateRedPieceAt(int index, int position, Direction dir) {
        Piece piece = red.getPieces()[index];
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(dir);
        board.placePiece(piece, position);
        return piece;
    }

    private Piece activateGreenPieceAt(int index, int position, Direction dir) {
        Piece piece = green.getPieces()[index];
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(dir);
        board.placePiece(piece, position);
        return piece;
    }

    private void forceNextRoll(int desiredRoll) {
        for (long seed = 0; seed < 10_000; seed++) {
            RandomInitiator.getInstance().setSeed(seed);
            if (RandomInitiator.getInstance().nextInt(6) + 1 == desiredRoll) {
                RandomInitiator.getInstance().setSeed(seed);
                return;
            }
        }
        fail("Could not find a seed to produce roll " + desiredRoll);
    }
}
