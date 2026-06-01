package test.test;

import engine.RuleEngine;
import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.MysteryCell;
import model.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleEngine")
class RuleEngineTest extends BaseTest {

    private Board board;
    private RuleEngine classicEngine;
    private RuleEngine ludoTEngine;

    @BeforeEach
    void setUp() {
        board = new Board();
        classicEngine = new RuleEngine(board, GameMode.CLASSIC);
        ludoTEngine   = new RuleEngine(board, GameMode.LUDO_T);
    }

    // Base piece movement
    @Test
    @DisplayName("should_returnInvalid_when_basePieceRollsLessThanSix")
    void should_returnInvalid_when_basePieceRollsLessThanSix() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        // state defaults to BASE

        // Act
        MoveResult result = classicEngine.validateMove(piece, 3);

        // Assert
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("should_returnValid_when_basePieceRollsSix")
    void should_returnValid_when_basePieceRollsSix() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);

        // Act
        MoveResult result = classicEngine.validateMove(piece, GameConstants.MAX_DICE_ROLL);

        // Assert
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("should_setTargetToStartX_when_basePieceRollsSix")
    void should_setTargetToStartX_when_basePieceRollsSix() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);

        // Act
        MoveResult result = classicEngine.validateMove(piece, GameConstants.MAX_DICE_ROLL);

        // Assert
        assertEquals(board.getStartX(Colour.RED), result.getTargetCell());
    }

    @Test
    @DisplayName("should_returnInvalid_when_basePieceStartXBlockedByOpponentBlock")
    void should_returnInvalid_when_basePieceStartXBlockedByOpponentBlock() {
        // Arrange â€” two Green pieces block Red's start X
        int redStart = board.getStartX(Colour.RED);
        Piece g1 = new Piece("G1", Colour.GREEN);
        Piece g2 = new Piece("G2", Colour.GREEN);
        board.placePiece(g1, redStart);
        board.placePiece(g2, redStart);

        Piece redPiece = new Piece("R1", Colour.RED);

        // Act
        MoveResult result = classicEngine.validateMove(redPiece, GameConstants.MAX_DICE_ROLL);

        // Assert
        assertFalse(result.isValid());
    }

    // Same-colour landing
    @Test
    @DisplayName("should_returnValid_when_pieceLandsOnSameColourPiece")
    void should_returnValid_when_pieceLandsOnSameColourPiece() {
        // Arrange â€” R1 at 10, R2 at 11 (both CW); R1 rolls 1 â†’ target = 11
        Piece r1 = new Piece("R1", Colour.RED);
        r1.setState(PieceState.ACTIVE);
        r1.moveTo(10);
        board.placePiece(r1, 10);

        Piece r2 = new Piece("R2", Colour.RED);
        r2.setState(PieceState.ACTIVE);
        r2.moveTo(11);
        board.placePiece(r2, 11);

        // Act
        MoveResult result = classicEngine.validateMove(r1, 1);

        // Assert
        assertTrue(result.isValid());
    }

    // Capture
    @Test
    @DisplayName("should_setCapture_when_pieceCanCaptureOpponent")
    void should_setCapture_when_pieceCanCaptureOpponent() {
        // Arrange â€” Red at 10, Green (single) at 11; roll 1 â†’ target = 11
        Piece redPiece = new Piece("R1", Colour.RED);
        redPiece.setState(PieceState.ACTIVE);
        redPiece.moveTo(10);
        board.placePiece(redPiece, 10);

        Piece greenPiece = new Piece("G1", Colour.GREEN);
        greenPiece.setState(PieceState.ACTIVE);
        greenPiece.moveTo(11);
        board.placePiece(greenPiece, 11);

        // Act
        MoveResult result = classicEngine.validateMove(redPiece, 1);

        // Assert
        assertTrue(result.isCapture());
        assertEquals(greenPiece, result.getCapturedPiece());
    }

    // Opponent block stops at adjacent
    @Test
    @DisplayName("should_stopAtAdjacentCell_when_opponentBlockInPath")
    void should_stopAtAdjacentCell_when_opponentBlockInPath() {
        // Arrange â€” Red at 12 (CW), two Green pieces at 15; roll 3 â†’ would land on 15
        Piece redPiece = new Piece("R1", Colour.RED);
        redPiece.setState(PieceState.ACTIVE);
        redPiece.moveTo(12);
        board.placePiece(redPiece, 12);

        Piece g1 = new Piece("G1", Colour.GREEN);
        Piece g2 = new Piece("G2", Colour.GREEN);
        g1.setState(PieceState.ACTIVE);
        g2.setState(PieceState.ACTIVE);
        g1.moveTo(15);
        g2.moveTo(15);
        board.placePiece(g1, 15);
        board.placePiece(g2, 15);

        // Act
        MoveResult result = classicEngine.validateMove(redPiece, 3);

        // Assert
        assertTrue(result.isBlockedAtAdjacent());
        // Adjacent cell before 15 in CW direction = 14
        assertEquals(14, result.getTargetCell());
    }

    // canEnterHome
    @Test
    @DisplayName("should_disallowHomeEntry_when_captureCountIsZeroInLudoT")
    void should_disallowHomeEntry_when_captureCountIsZeroInLudoT() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        // captureCount = 0 by default

        // Assert
        assertFalse(ludoTEngine.canEnterHome(piece));
    }

    @Test
    @DisplayName("should_allowHomeEntry_when_captureCountIsOneInLudoT")
    void should_allowHomeEntry_when_captureCountIsOneInLudoT() {
        // Arrange
        Piece piece = new Piece("R1", Colour.RED);
        piece.capture();

        // Assert
        assertTrue(ludoTEngine.canEnterHome(piece));
    }

    @Test
    @DisplayName("should_alwaysAllowHomeEntry_when_classicMode")
    void should_alwaysAllowHomeEntry_when_classicMode() {
        // Arrange â€” piece with zero captures
        Piece piece = new Piece("R1", Colour.RED);

        // Assert
        assertTrue(classicEngine.canEnterHome(piece));
    }

    // Mystery cell in CLASSIC mode
    @Test
    @DisplayName("should_notTriggerMystery_when_classicMode")
    void should_notTriggerMystery_when_classicMode() {
        // Arrange â€” activate mystery cell, then move a piece over it in CLASSIC mode
        Piece dummy = new Piece("D1", Colour.GREEN);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(0);
        board.placePiece(dummy, 0);

        MysteryCell mystery = new MysteryCell(GameConstants.NO_POSITION);
        mystery.spawn(board);
        board.setMysteryCell(mystery);

        int mysteryPos = board.getMysteryPosition();

        // Place Red piece one step behind mystery
        int startPos = (mysteryPos - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        Piece redPiece = new Piece("R1", Colour.RED);
        redPiece.setState(PieceState.ACTIVE);
        redPiece.moveTo(startPos);
        board.placePiece(redPiece, startPos);

        // Act â€” roll 1 in CLASSIC mode should land on mystery cell but not trigger it
        MoveResult result = classicEngine.validateMove(redPiece, 1);

        // Assert
        assertFalse(result.isMystery());
    }
}

