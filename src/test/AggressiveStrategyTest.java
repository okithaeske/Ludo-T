package test;

import engine.EffectHandler;
import engine.RuleEngine;
import engine.TurnExecutor;
import engine.TurnManager;
import enums.*;
import logger.GameEventListener;
import logger.GameEventPublisher;
import model.*;
import player.AbstractPlayer;
import player.GreenPlayer;
import player.RedPlayer;
import player.strategy.AggressiveStrategy;
import player.strategy.BlockerStrategy;
import player.strategy.PieceSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AggressiveStrategy (Red)")
class AggressiveStrategyTest extends BaseTest {

    private RedPlayer red;
    private Board board;
    private PieceSelectionStrategy strategy;

    @BeforeEach
    void setUp() {
        red = new RedPlayer();
        board = new Board();
        strategy = new AggressiveStrategy(red);
    }

    /** Helper: activate a player piece at the given position (CW direction). */
    private Piece activatePieceAt(int pieceIndex, int position) {
        Piece piece = red.getPieces()[pieceIndex];
        piece.setState(PieceState.ACTIVE);
        piece.moveTo(position);
        piece.assignInitialDirection(Direction.CW);
        board.placePiece(piece, position);
        return piece;
    }

    /** Helper: place a standalone opponent piece on the board. */
    private Piece placeOpponentAt(Colour colour, int position) {
        Piece opponent = new Piece(colour.name().charAt(0) + "1", colour);
        opponent.setState(PieceState.ACTIVE);
        opponent.moveTo(position);
        opponent.assignInitialDirection(Direction.CW);
        board.placePiece(opponent, position);
        return opponent;
    }

    //  Capture priority
    @Test
    @DisplayName("should_selectAttacker_when_opponentIsInCaptureRange")
    void should_selectAttacker_when_opponentIsInCaptureRange() {
        // Arrange Red at 10 (CW), Green single piece at 11; roll 1 Ã¢â€ â€™ target = 11
        Piece redPiece = activatePieceAt(0, 10);
        placeOpponentAt(Colour.GREEN, 11);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert
        Assertions.assertEquals(redPiece, selected);
    }

    @Test
    @DisplayName("should_pickAttackerTargetingOpponentClosestToTheirHome_when_multipleCapturesAvailable")
    void should_pickAttackerTargetingOpponentClosestToTheirHome_when_multipleCapturesAvailable() {
        // Arrange:
        //   R1 at 10 (CW), roll 1 Ã¢â€ â€™ targets Green at 11.
        //       Green at 11 (CW), GREEN_APPROACH=37: dist = (37-11+52)%52 = 26
        //   R2 at 20 (CW), roll 1 Ã¢â€ â€™ targets Green at 21.
        //       Green at 21 (CW): dist = (37-21+52)%52 = 16  Ã¢â€ Â closer to Green's home
        // AggressiveStrategy must return R2 because its target is the nearer Green.
        Piece r1 = activatePieceAt(0, 10);
        Piece r2 = activatePieceAt(1, 20);
        placeOpponentAt(Colour.GREEN, 11);
        placeOpponentAt(Colour.GREEN, 21);

        // Act
        Piece selected = strategy.choosePiece(1, board);

        // Assert
        Assertions.assertEquals(r2, selected);
    }

    // No valid move
    @Test
    @DisplayName("should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix")
    void should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix() {
        // Arrange Ã¢â‚¬â€ all Red pieces remain at BASE (default), roll=3

        // Act
        Piece selected = strategy.choosePiece(3, board);

        // Assert
        Assertions.assertTrue(selected.isNull());
    }

    //  Base exit on roll 6 Test
    @DisplayName("should_bringBasePiece_when_rollIsSix_andNoBoardPiecesExist")
    void should_bringBasePiece_when_rollIsSix_andNoBoardPiecesExist() {
        // Arrange Ã¢â‚¬â€ all pieces at BASE, no board pieces

        // Act
        Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

        // Assert Ã¢â‚¬â€ must not be null; must be the first base piece
        Assertions.assertFalse(selected.isNull());
        Assertions.assertEquals(PieceState.BASE, selected.getState());
    }

    /**
     * Scenario tests â€” verify that the full pipeline
     * (roll â†’ strategy â†’ validation â†’ apply â†’ board state)
     * produces the correct observable board state after a complete turn.
     *
     * Each test sets up a specific game position, forces a deterministic dice
     * roll via seed search, executes a full turn through TurnExecutor, and then
     * inspects the Board and Piece state directly.
     */
    @DisplayName("Scenario (full-pipeline board-state)")
    static
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
            publisher.addListener(new GameEventListener() { }); // silent â€” suppresses output
            RuleEngine ruleEngine = new RuleEngine(board, GameMode.LUDO_T);
            EffectHandler effectHandler = new EffectHandler(
                    board, turnManager, publisher, ruleEngine, players);
            executor = new TurnExecutor(
                    board, ruleEngine, turnManager, publisher,
                    effectHandler, GameMode.LUDO_T, players);
        }

        // â”€â”€ Scenario 1: standard movement â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_updateBoardPosition_when_pieceMovesOnStandardPath")
        void should_updateBoardPosition_when_pieceMovesOnStandardPath() {
            // Arrange â€” Red piece at 10 (CW), roll 3 â†’ should land at 13.
            Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
            forceNextRoll(3);

            // Act
            executor.executeTurn(red, false);

            // Assert â€” piece is at 13, old cell is vacated
            Assertions.assertEquals(13, r1.getPosition());
            Assertions.assertTrue(board.getPiecesAt(13).contains(r1));
            Assertions.assertTrue(board.getPiecesAt(10).isEmpty());
        }

        // â”€â”€ Scenario 2: capture â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_placeAttackerAtCaptureCell_and_returnVictimToBase_when_captureOccurs")
        void should_placeAttackerAtCaptureCell_and_returnVictimToBase_when_captureOccurs() {
            // Arrange â€” Red at 10, lone Green at 11. Roll 1 â†’ Red captures Green.
            Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
            Piece g1 = activateGreenPieceAt(0, 11, Direction.CW);
            forceNextRoll(1);

            // Act
            executor.executeTurn(red, false);

            // Assert â€” Red now occupies cell 11; Green is fully reset to base.
            Assertions.assertEquals(11, r1.getPosition());
            Assertions.assertTrue(board.getPiecesAt(11).contains(r1));

            Assertions.assertEquals(GameConstants.NO_POSITION, g1.getPosition());
            Assertions.assertEquals(PieceState.BASE, g1.getState());
            Assertions.assertFalse(board.getPiecesAt(11).contains(g1));
        }

        // â”€â”€ Scenario 3: block movement â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_moveBothPiecesTogether_when_blockAdvances")
        void should_moveBothPiecesTogether_when_blockAdvances() {
            // Arrange â€” two Red pieces form a block at position 10.
            // Roll 4: block steps = 4 / 2 = 2 â†’ both pieces land at 12.
            Piece r1 = activateRedPieceAt(0, 10, Direction.CW);
            Piece r2 = activateRedPieceAt(1, 10, Direction.CW);
            forceNextRoll(4);

            // Act
            executor.executeTurn(red, false);

            // Assert â€” both pieces moved to 12; cell 10 is now empty.
            Assertions.assertEquals(12, r1.getPosition());
            Assertions.assertEquals(12, r2.getPosition());
            Assertions.assertTrue(board.getPiecesAt(12).contains(r1));
            Assertions.assertTrue(board.getPiecesAt(12).contains(r2));
            Assertions.assertTrue(board.getPiecesAt(10).isEmpty());
        }

        // â”€â”€ Scenario 4: base exit â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_placePieceAtStartCell_when_exitingBase")
        void should_placePieceAtStartCell_when_exitingBase() {
            // Arrange â€” all Red pieces at base; roll 6 brings one piece to RED_START.
            forceNextRoll(6);

            // Act
            executor.executeTurn(red, false);

            // Assert â€” exactly one Red piece is on the board at RED_START.
            List<Piece> atStart = board.getPiecesAt(GameConstants.RED_START);
            Assertions.assertEquals(1, atStart.size());
            Assertions.assertEquals(PieceState.ACTIVE, atStart.get(0).getState());
            Assertions.assertEquals(Colour.RED, atStart.get(0).getColour());
        }

        // â”€â”€ Scenario 5: home-straight entry â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_enterHomeStraightAtCorrectPosition_when_piecePassesApproachCell")
        void should_enterHomeStraightAtCorrectPosition_when_piecePassesApproachCell() {
            // Arrange â€” Red piece at 22 (CW); RED_APPROACH = 24.
            // distToApproach = (24 - 22 + 52) % 52 = 2.
            // Roll 3 â†’ steps = 3 > 2 â†’ enters home straight at position 1 (3 - 2 = 1).
            // Piece needs 1 capture to satisfy canEnterHome in Ludo-T.
            Piece r1 = activateRedPieceAt(0, 22, Direction.CW);
            r1.capture();
            forceNextRoll(3);

            // Act
            executor.executeTurn(red, false);

            // Assert â€” piece is in the home straight, not on the standard board.
            Assertions.assertTrue(r1.isInHomeStraight());
            Assertions.assertEquals(1, r1.getHomeStraightPosition());
            Assertions.assertEquals(GameConstants.NO_POSITION, r1.getPosition());
            Assertions.assertTrue(board.getPiecesAt(22).isEmpty());
        }

        // â”€â”€ Scenario 6: mystery cell teleport â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        @Test
        @DisplayName("should_teleportPieceAwayFromMysteryCell_when_mysteryTriggers")
        void should_teleportPieceAwayFromMysteryCell_when_mysteryTriggers() {
            // Arrange â€” spawn the mystery cell (requires a piece on the board), record its
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

            // Assert â€” piece left its start position and the teleport event was published.
            Assertions.assertFalse(board.getPiecesAt(startPos).contains(r1),
                    "Piece should not remain at pre-teleport position");
            Assertions.assertTrue(teleportFired.get(),
                    "onTeleport must fire when a piece lands on the mystery cell");
        }

        // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            Assertions.fail("Could not find a seed to produce roll " + desiredRoll);
        }
    }

    @DisplayName("BlockerStrategy (Green)")
    static
    class BlockerStrategyTest extends BaseTest {

        private GreenPlayer green;
        private Board board;
        private PieceSelectionStrategy strategy;

        @BeforeEach
        void setUp() {
            green = new GreenPlayer();
            board = new Board();
            strategy = new BlockerStrategy(green);
        }

        // Base exit on roll 6
        @Test
        @DisplayName("should_exitBase_when_rollIsSixAndBasePieceExists")
        void should_exitBase_when_rollIsSixAndBasePieceExists() {
            // Arrange Ã¢â‚¬â€ all pieces at BASE (default)

            // Act
            Piece selected = strategy.choosePiece(GameConstants.MAX_DICE_ROLL, board);

            // Assert
            Assertions.assertFalse(selected.isNull());
            Assertions.assertEquals(PieceState.BASE, selected.getState());
        }

        //  Block formation
        @Test
        @DisplayName("should_moveToFormBlock_when_friendlyPieceIsAtTargetCell")
        void should_moveToFormBlock_when_friendlyPieceIsAtTargetCell() {
            // Arrange:
            //   G1 at 10 (CW), G2 at 11 (CW)
            //   roll = 1 Ã¢â€ â€™ G1's target = 11 where G2 already is Ã¢â€ â€™ block formation
            Piece[] pieces = green.getPieces();

            pieces[0].setState(PieceState.ACTIVE);
            pieces[0].moveTo(10);
            pieces[0].assignInitialDirection(Direction.CW);
            board.placePiece(pieces[0], 10);

            pieces[1].setState(PieceState.ACTIVE);
            pieces[1].moveTo(11);
            pieces[1].assignInitialDirection(Direction.CW);
            board.placePiece(pieces[1], 11);

            // Act
            Piece selected = strategy.choosePiece(1, board);

            // Assert Ã¢â‚¬â€ G1 should be selected to stack onto G2
            Assertions.assertEquals(pieces[0], selected);
        }

        //  No valid move
        @Test
        @DisplayName("should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix")
        void should_returnNoPiece_when_allPiecesAreAtBase_andRollIsNotSix() {
            // Arrange Ã¢â‚¬â€ all pieces at BASE, roll = 3

            // Act
            Piece selected = strategy.choosePiece(3, board);

            // Assert
            Assertions.assertTrue(selected.isNull());
        }

        //  Avoid capture fallback
        @Test
        @DisplayName("should_moveLeastAdvancedPiece_when_avoidingCapture")
        void should_moveLeastAdvancedPiece_when_avoidingCapture() {
            // Arrange:
            //   G1 at 5  (CW), GREEN_APPROACH=37: dist = (37-5+52)%52 = 32 (far from home)
            //   G2 at 30 (CW): dist = (37-30+52)%52 = 7  (close to home)
            //   No opponents Ã¢â€ â€™ no capture needed.
            //   BlockerStrategy avoidCapture() picks piece furthest from home (G1).
            Piece[] pieces = green.getPieces();

            pieces[0].setState(PieceState.ACTIVE);
            pieces[0].moveTo(5);
            pieces[0].assignInitialDirection(Direction.CW);
            board.placePiece(pieces[0], 5);

            pieces[1].setState(PieceState.ACTIVE);
            pieces[1].moveTo(30);
            pieces[1].assignInitialDirection(Direction.CW);
            board.placePiece(pieces[1], 30);

            // Act roll 1 (no block-formation move possible with only one piece at each cell)
            Piece selected = strategy.choosePiece(1, board);

            // Assert Ã¢â‚¬â€ G1 (pieces[0]) is furthest from home Ã¢â€ â€™ least advanced Ã¢â€ â€™ selected
            Assertions.assertEquals(pieces[0], selected);
        }
    }
}

