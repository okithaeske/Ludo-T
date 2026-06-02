package engine;

import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventPublisher;
import model.Block;
import model.BlockBreakMove;
import model.BlockMoveResult;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

/** Executes one player turn end-to-end. */
public class TurnExecutor {

    private final Board board;
    private final RuleEngine ruleEngine;
    private final TurnManager turnManager;
    private final GameEventPublisher publisher;
    private final EffectHandler effectHandler;
    private final GameMode gameMode;
    private final List<AbstractPlayer> players;

    public TurnExecutor(Board board, RuleEngine ruleEngine, TurnManager turnManager,
                        GameEventPublisher publisher, EffectHandler effectHandler,
                        GameMode gameMode, List<AbstractPlayer> players) {
        this.board = board;
        this.ruleEngine = ruleEngine;
        this.turnManager = turnManager;
        this.publisher = publisher;
        this.effectHandler = effectHandler;
        this.gameMode = gameMode;
        this.players = players;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void executeTurn(AbstractPlayer player, boolean isExtraRoll) {
        int roll = turnManager.rollDice(player);
        publisher.publishRoll(player, roll);

        if (handleTripleSix(player, isExtraRoll)) return;
        if (handleFrozenEscape(player, isExtraRoll)) return;
        handleNormalTurn(player, roll, isExtraRoll);
    }

    // ── Turn-level handlers ───────────────────────────────────────────────────

    private boolean handleTripleSix(AbstractPlayer player, boolean isExtraRoll) {
        if (!turnManager.isTripleSix()) return false;
        if (gameMode.isLudoT() && playerHasBlock(player)) {
            applyBlockBreak(ruleEngine.planBlockBreak(player));
        }
        turnManager.handleTripleSix();
        finishTurn(player, isExtraRoll, false);
        return true;
    }

    private boolean handleFrozenEscape(AbstractPlayer player, boolean isExtraRoll) {
        if (!effectHandler.isFrozenEscape(player)) return false;
        effectHandler.handleFrozenEscape(player);
        finishTurn(player, isExtraRoll, true);
        return true;
    }

    private void handleNormalTurn(AbstractPlayer player, int roll, boolean isExtraRoll) {
        Piece chosenPiece = player.choosePiece(roll, board);
        if (chosenPiece.isNull() || chosenPiece.isMovementRestricted()) {
            publisher.publishNoValidMove(player);
            finishTurn(player, isExtraRoll, false);
            return;
        }

        Block currentBlock = buildBlockForPiece(chosenPiece);
        MoveResult result = validateMove(chosenPiece, currentBlock, roll);

        if (result.isValid()) {
            applyMove(chosenPiece, result, player, roll, currentBlock);
            handleExtraRoll(roll);
        }

        finishTurn(player, isExtraRoll, result.getTeleportDest() != null);
    }

    private MoveResult validateMove(Piece piece, Block block, int roll) {
        return (block != null)
                ? ruleEngine.validateBlockMove(block, roll)
                : ruleEngine.validateMove(piece, roll);
    }

    private void finishTurn(AbstractPlayer player, boolean isExtraRoll, boolean skipTick) {
        if (!isExtraRoll && !skipTick) {
            effectHandler.tickPlayerEffects(player);
        }
    }

    // ── Move application ──────────────────────────────────────────────────────

    private void applyMove(Piece piece, MoveResult result, AbstractPlayer player,
                           int roll, Block currentBlock) {
        int fromCell = piece.getPosition();

        if (applySpecialMove(piece, result, player, fromCell, currentBlock, roll)) return;

        trackCCWApproachPass(piece, fromCell, result);
        publishBlockedIfAdjacent(piece, result, currentBlock, fromCell);
        resolveCaptures(piece, result, currentBlock);
        resolveTeleport(piece, result);
    }

    /**
     * Handles home, home-straight, or block moves.
     *
     * @return true when further processing should stop.
     */
    private boolean applySpecialMove(Piece piece, MoveResult result, AbstractPlayer player,
                                     int fromCell, Block currentBlock, int roll) {
        if (result.isHome()) {
            movePieceHome(piece, fromCell);
            return true;
        }
        if (result.isEnteringHomeStraight() || result.isMovingInHomeStraight()) {
            applyHomeStraightMove(piece, result, fromCell);
            return true;
        }
        if (currentBlock != null) {
            boolean appliedOk = applyBlockMove(piece, result, currentBlock, fromCell, roll);
            return !appliedOk;
        }
        applySinglePieceMove(piece, result, player, fromCell);
        return false;
    }

    private void trackCCWApproachPass(Piece piece, int fromCell, MoveResult result) {
        if (gameMode.isLudoT()
                && piece.getDirection() == Direction.CCW
                && !result.isHome()
                && fromCell != GameConstants.NO_POSITION) {
            trackApproachPass(piece, fromCell, piece.getPosition());
        }
    }

    private void publishBlockedIfAdjacent(Piece piece, MoveResult result,
                                          Block currentBlock, int fromCell) {
        if (!result.isBlockedAtAdjacent() || currentBlock != null) return;
        int blockingCell = result.getBlockedAt();
        publisher.publishPieceBlocked(piece, fromCell, blockingCell,
                board.getPiecesAt(blockingCell));
        publisher.publishPieceBlockedAtAdjacent(piece, blockingCell, result.getTargetCell());
    }

    private void resolveCaptures(Piece piece, MoveResult result, Block currentBlock) {
        if (result.isBlockCapture() && currentBlock != null) {
            AbstractPlayer victimPlayer = findPlayerByColour(result.getDefenderBlock().getColour());
            ruleEngine.applyBlockCapture(currentBlock, result.getDefenderBlock());
            publisher.publishBlockCapture(currentBlock, result.getDefenderBlock());
            publisher.publishCapturedPlayerStatus(victimPlayer);
            turnManager.grantExtraRoll();
        } else if (result.isCapture() && currentBlock != null) {
            handleBlockCapture(currentBlock, result);
        } else if (result.isCapture()) {
            handleCapture(piece, result);
        }
    }

    private void resolveTeleport(Piece piece, MoveResult result) {
        if (result.getTeleportDest() != null) {
            effectHandler.handleTeleport(piece, result);
        }
    }

    // ── Movement helpers ──────────────────────────────────────────────────────

    private void applyHomeStraightMove(Piece piece, MoveResult result, int fromCell) {
        int oldHomePos = piece.getHomeStraightPosition(); // save before update
        if (fromCell >= 0 && fromCell < GameConstants.BOARD_SIZE) {
            board.removePiece(piece, fromCell);
        }
        piece.leaveStandardPathForHomeStraight(result.getHomeStraightPosition());
        piece.clearMovementEffects();
        int steps = result.getHomeStraightPosition() - oldHomePos;
        publisher.publishMove(piece, fromCell, result.getHomeStraightPosition(),
                piece.getDirection(), steps);
    }

    private boolean applyBlockMove(Piece piece, MoveResult result, Block currentBlock,
                                   int fromCell, int roll) {
        if (result.isBlockedAtAdjacent()) {
            moveBlockToAdjacentCell(piece, result, currentBlock, fromCell);
        } else {
            BlockMoveResult blockResult = ruleEngine.resolveBlock(currentBlock, roll);
            piece.setState(PieceState.ACTIVE);
            if (blockResult.brokeAtApproach()) {
                publisher.publishBlockBrokenAtApproach(currentBlock, blockResult.getApproachCell());
                return false;
            }
            int steps = roll / currentBlock.getSize();
            publisher.publishMove(piece, fromCell, piece.getPosition(),
                    piece.getDirection(), steps);
        }
        return true;
    }

    private void moveBlockToAdjacentCell(Piece piece, MoveResult result,
                                         Block currentBlock, int fromCell) {
        int targetCell = result.getTargetCell();
        for (Piece bp : currentBlock.getPieces()) {
            board.removePiece(bp, bp.getPosition());
            bp.moveTo(targetCell);
            board.placePiece(bp, targetCell);
        }
        piece.setState(PieceState.ACTIVE);
        int steps = stepsFromPositions(fromCell, targetCell, piece.getDirection());
        publisher.publishMove(piece, fromCell, targetCell, piece.getDirection(), steps);
        int blockedAt = result.getBlockedAt();
        publisher.publishPieceBlockedAtAdjacent(piece, blockedAt, targetCell);
    }

    private void applySinglePieceMove(Piece piece, MoveResult result, AbstractPlayer player,
                                      int fromCell) {
        board.removePiece(piece, piece.getPosition());
        piece.moveTo(result.getTargetCell());
        board.placePiece(piece, result.getTargetCell());

        if (result.getTeleportDest() != TeleportDest.BASE) {
            piece.setState(PieceState.ACTIVE);
        }

        if (fromCell == GameConstants.NO_POSITION) {
            effectHandler.performCoinToss(piece);
            publisher.publishPieceMoveToX(piece,
                    player.getPiecesOnBoard().size(),
                    player.getPiecesAtBase().size());
        } else {
            int steps = stepsFromPositions(fromCell, result.getTargetCell(), piece.getDirection());
            publisher.publishMove(piece, fromCell, result.getTargetCell(),
                    piece.getDirection(), steps);
        }
    }

    private void handleCapture(Piece piece, MoveResult result) {
        Piece capturedPiece = result.getCapturedPiece();
        AbstractPlayer victimPlayer = findPlayerByColour(capturedPiece.getColour());
        publisher.publishCapture(piece, capturedPiece);
        piece.capture();
        board.removePiece(capturedPiece, capturedPiece.getPosition());
        capturedPiece.reset();
        publisher.publishCapturedPlayerStatus(victimPlayer);
        turnManager.grantExtraRoll();
    }

    private void handleBlockCapture(Block currentBlock, MoveResult result) {
        Piece capturedPiece = result.getCapturedPiece();
        AbstractPlayer victimPlayer = findPlayerByColour(capturedPiece.getColour());
        for (Piece blockPiece : currentBlock.getPieces()) {
            blockPiece.capture();
        }
        board.removePiece(capturedPiece, capturedPiece.getPosition());
        capturedPiece.reset();
        publisher.publishCapturedPlayerStatus(victimPlayer);
        turnManager.grantExtraRoll();
    }

    private AbstractPlayer findPlayerByColour(Colour colour) {
        for (AbstractPlayer p : players) {
            if (p.getColour() == colour) return p;
        }
        throw new IllegalStateException("No player found for colour " + colour);
    }

    private void movePieceHome(Piece piece, int fromCell) {
        int oldHomePos = piece.getHomeStraightPosition(); // save before reset
        if (fromCell >= 0 && fromCell < GameConstants.BOARD_SIZE) {
            board.removePiece(piece, fromCell);
        }
        piece.moveTo(GameConstants.NO_POSITION);
        piece.setHomeStraightPosition(0);
        piece.setState(PieceState.HOME);
        int steps = GameConstants.HOME_EXIT_DISTANCE - oldHomePos;
        publisher.publishMove(piece, fromCell, GameConstants.BOARD_SIZE,
                piece.getDirection(), steps);
    }

    // ── Block utilities ───────────────────────────────────────────────────────

    private Block buildBlockForPiece(Piece piece) {
        if (piece.isInHomeStraight()) return null;
        if (piece.getPosition() == board.getApproach(piece.getColour())) return null;

        Block block = board.getBlockAt(piece.getPosition(), piece.getColour(), piece.getDirection());
        if (block != null && block.getSize() >= GameConstants.MIN_BLOCK_SIZE) {
            block.setDirection(resolveMixedBlockDirection(block));
        }
        return block;
    }

    private Direction resolveMixedBlockDirection(Block block) {
        Direction direction = block.getPieces().get(0).getDirection();
        for (Piece p : block.getPieces()) {
            if (p.getDirection() != direction) {
                return ruleEngine.resolveBlockDirection(block.getPieces().get(0), p, board);
            }
        }
        return direction;
    }

    private boolean playerHasBlock(AbstractPlayer player) {
        java.util.Map<Integer, Integer> posCount = new java.util.HashMap<>();
        for (Piece piece : player.getPiecesOnBoard()) {
            if (piece.getPosition() >= 0 && piece.getPosition() < GameConstants.BOARD_SIZE) {
                posCount.merge(piece.getPosition(), 1, Integer::sum);
            }
        }
        return posCount.values().stream().anyMatch(c -> c >= GameConstants.MIN_BLOCK_SIZE);
    }

    private void applyBlockBreak(List<BlockBreakMove> moves) {
        for (BlockBreakMove m : moves) {
            board.removePiece(m.getPiece(), m.getFromPos());
            m.getPiece().setDirection(m.getDirection());
            m.getPiece().moveTo(m.getNewPos());
            board.placePiece(m.getPiece(), m.getNewPos());
            publisher.publishMove(m.getPiece(), m.getFromPos(), m.getNewPos(),
                    m.getDirection(), m.getDistance());
        }
    }

    private void trackApproachPass(Piece piece, int fromPos, int targetPos) {
        if (targetPos == GameConstants.NO_POSITION) return;
        int approachCell = board.getApproach(piece.getColour());
        int distFromToApproach = (fromPos - approachCell + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        int stepsActual = (fromPos - targetPos + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        boolean crossedApproach = distFromToApproach > 0 && distFromToApproach <= stepsActual;
        boolean leftApproach = fromPos == approachCell && stepsActual > 0;
        if (crossedApproach || leftApproach) {
            piece.incrementApproachPass();
        }
    }

    private void handleExtraRoll(int roll) {
        if (roll == GameConstants.MAX_DICE_ROLL) {
            turnManager.grantExtraRoll();
        }
    }

    private int stepsFromPositions(int from, int to, Direction direction) {
        if (direction == Direction.CW) {
            return (to - from + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        return (from - to + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
    }
}
