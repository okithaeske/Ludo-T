package test.test;

import engine.RuleEngine;
import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import model.BlockBreakMove;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.MysteryCell;
import model.Piece;
import player.RedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // Standard path movement (Rule 1)
    @Test
    @DisplayName("should_returnValidMove_when_pieceMovesExactCellsOnStandardPath")
    void should_returnValidMove_when_pieceMovesExactCellsOnStandardPath() {
        // Arrange — Red at 10 (CW), roll 3 → should land at 13
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(10);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, 10);

        // Act
        MoveResult result = classicEngine.validateMove(piece, 3);

        // Assert
        assertTrue(result.isValid());
        assertEquals(13, result.getTargetCell());
    }

    // Over-stacking own piece (Rule 7)
    @Test
    @DisplayName("should_returnInvalidMove_when_pieceTriesToLandOnOwnPiece")
    void should_returnInvalidMove_when_pieceTriesToLandOnOwnPiece() {
        // Arrange — two Red pieces already form a block at cell 15; a 3rd Red piece at 12
        // tries to join. A block is always exactly size 2, so a 3rd piece must be rejected.
        Piece r1 = new Piece("R1", Colour.RED);
        Piece r2 = new Piece("R2", Colour.RED);
        Piece r3 = new Piece("R3", Colour.RED);
        for (Piece p : new Piece[]{r1, r2, r3}) {
            p.setState(PieceState.ACTIVE);
            p.assignInitialDirection(Direction.CW);
        }
        r1.moveTo(15); board.placePiece(r1, 15);
        r2.moveTo(15); board.placePiece(r2, 15);
        r3.moveTo(12); board.placePiece(r3, 12);

        // Act
        MoveResult result = classicEngine.validateMove(r3, 3);

        // Assert — joining a full block is not permitted
        assertFalse(result.isValid());
    }

    // Jumping over a single opponent (Rule 5)
    @Test
    @DisplayName("should_allowMove_when_pieceJumpsOverAnotherPiece")
    void should_allowMove_when_pieceJumpsOverAnotherPiece() {
        // Arrange — Red at 10 (CW); lone Green at 12 (not a block); roll 5 → target 15
        // A single opponent piece does not block movement — only a 2-piece block does.
        Piece red = new Piece("R1", Colour.RED);
        red.setState(PieceState.ACTIVE);
        red.moveTo(10);
        red.assignInitialDirection(Direction.CW);
        board.placePiece(red, 10);

        Piece green = new Piece("G1", Colour.GREEN);
        green.setState(PieceState.ACTIVE);
        green.moveTo(12);
        green.assignInitialDirection(Direction.CW);
        board.placePiece(green, 12);

        // Act
        MoveResult result = classicEngine.validateMove(red, 5);

        // Assert — Red jumps over the single Green piece unimpeded
        assertTrue(result.isValid());
        assertEquals(15, result.getTargetCell());
        assertFalse(result.isBlockedAtAdjacent());
    }

    // Mystery cell triggered in Ludo-T (Rule T-11)
    @Test
    @DisplayName("should_triggerMystery_when_pieceLandsOnMysteryCell_inLudoTMode")
    void should_triggerMystery_when_pieceLandsOnMysteryCell_inLudoTMode() {
        // Arrange — spawn mystery cell, then place Red one step behind it
        Piece dummy = new Piece("D1", Colour.GREEN);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(0);
        board.placePiece(dummy, 0);

        MysteryCell mystery = new MysteryCell(GameConstants.NO_POSITION);
        mystery.spawn(board);
        board.setMysteryCell(mystery);

        int mysteryPos = board.getMysteryPosition();
        int startPos = (mysteryPos - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;

        Piece red = new Piece("R1", Colour.RED);
        red.setState(PieceState.ACTIVE);
        red.moveTo(startPos);
        red.assignInitialDirection(Direction.CW);
        board.placePiece(red, startPos);

        // Act — roll 1 should land exactly on mystery cell
        MoveResult result = ludoTEngine.validateMove(red, 1);

        // Assert
        assertTrue(result.isValid());
        assertTrue(result.isMystery());
    }

    // Exact roll required inside home straight (Rule 10)
    @Test
    @DisplayName("should_requireExactRoll_when_pieceIsInHomeStraight")
    void should_requireExactRoll_when_pieceIsInHomeStraight() {
        // Arrange — piece at home-straight position 3; needs exactly 3 more steps to reach HOME.
        // HOME_EXIT_DISTANCE = 6, so 3+4=7 > 6 → overshoot → invalid.
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.leaveStandardPathForHomeStraight(3);

        // Act
        MoveResult result = classicEngine.validateMove(piece, 4);

        // Assert
        assertFalse(result.isValid());
    }

    // CCW second approach-pass requirement (Rule T-1)
    @Test
    @DisplayName("should_requireSecondApproachPass_when_movingCounterClockwise")
    void should_requireSecondApproachPass_when_movingCounterClockwise() {
        // Arrange — Red CCW piece at 26; RED_APPROACH = 24.
        // CCW distToApproach = (26-24+52)%52 = 2. Roll 3 crosses the approach.
        // approachPassCount = 0 → passes after this move = 1 < APPROACH_PASS_REQUIRED_CCW (2).
        // tryEnterHomeStraight returns null → piece moves normally to (26-3+52)%52 = 23.
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(26);
        piece.assignInitialDirection(Direction.CCW);
        piece.capture(); // satisfies canEnterHome
        board.placePiece(piece, 26);

        // Act
        MoveResult result = ludoTEngine.validateMove(piece, 3);

        // Assert — move is valid but did NOT enter home straight (first pass not yet completed)
        assertTrue(result.isValid());
        assertFalse(result.isEnteringHomeStraight());
        assertEquals(23, result.getTargetCell());
    }

    // Block direction: longest distance from home (Rule T-4)
    @Test
    @DisplayName("should_moveBlockInDirectionOfLongestDistanceFromHome_when_blockHasMixedDirections")
    void should_moveBlockInDirectionOfLongestDistanceFromHome_when_blockHasMixedDirections() {
        // Arrange — two Red pieces at position 20 with opposite directions.
        // R1 CW: dist = (RED_APPROACH - 20 + 52) % 52 = (24-20+52)%52 = 4
        // R2 CCW: dist = (20 - RED_APPROACH + 52) % 52 = (20-24+52)%52 = 48
        // R2 is further from home → block should move in R2's direction (CCW).
        Piece r1 = new Piece("R1", Colour.RED);
        r1.setState(PieceState.ACTIVE);
        r1.moveTo(20);
        r1.assignInitialDirection(Direction.CW);
        board.placePiece(r1, 20);

        Piece r2 = new Piece("R2", Colour.RED);
        r2.setState(PieceState.ACTIVE);
        r2.moveTo(20);
        r2.assignInitialDirection(Direction.CCW);
        board.placePiece(r2, 20);

        // Act
        Direction resolved = classicEngine.resolveBlockDirection(r1, r2, board);

        // Assert
        assertEquals(Direction.CCW, resolved);
    }

    // Force break block on triple six (Rule T-6)
    @Test
    @DisplayName("should_forceBreakBlock_when_threeConsecutiveSixesRolledWithBlockade")
    void should_forceBreakBlock_when_threeConsecutiveSixesRolledWithBlockade() {
        // Arrange — Red player with 2 pieces forming a block at position 10.
        // planBlockBreak scatters the 2nd piece by TRIPLE_SIX_BLOCKADE_MOVE = 6 cells.
        RedPlayer red = new RedPlayer();
        Piece r1 = red.getPieces()[0];
        Piece r2 = red.getPieces()[1];

        r1.setState(PieceState.ACTIVE);
        r1.moveTo(10);
        r1.assignInitialDirection(Direction.CW);
        board.placePiece(r1, 10);

        r2.setState(PieceState.ACTIVE);
        r2.moveTo(10);
        r2.assignInitialDirection(Direction.CW);
        board.placePiece(r2, 10);

        // Act
        List<BlockBreakMove> moves = classicEngine.planBlockBreak(red);

        // Assert — exactly one piece must be moved (the 2nd piece in the block)
        assertEquals(1, moves.size());
        BlockBreakMove move = moves.get(0);
        assertEquals(10, move.getFromPos());
        assertEquals((10 + GameConstants.TRIPLE_SIX_BLOCKADE_MOVE) % GameConstants.BOARD_SIZE,
                move.getNewPos());
    }

    // No effect when landing on Alpha/Beta/Gamma via normal movement (Rule T-15)
    @Test
    @DisplayName("should_notApplyEffect_when_pieceLandsOnAlphaBetaGammaWithoutTeleport")
    void should_notApplyEffect_when_pieceLandsOnAlphaBetaGammaWithoutTeleport() {
        // Arrange — piece moves normally to ALPHA_CELL (no mystery cell present).
        // ALPHA_CELL = 7; place piece 2 steps before it.
        int startPos = (GameConstants.ALPHA_CELL - 2 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        Piece piece = new Piece("R1", Colour.RED);
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(startPos);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, startPos);
        // No mystery cell set → board.getMysteryCell() == null

        // Act
        MoveResult result = ludoTEngine.validateMove(piece, 2);

        // Assert — valid move landing on alpha but NOT flagged as mystery
        assertTrue(result.isValid());
        assertEquals(GameConstants.ALPHA_CELL, result.getTargetCell());
        assertFalse(result.isMystery());
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

