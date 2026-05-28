package engine;

import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceEffect;
import enums.PieceState;
import model.Block;
import model.BlockMoveResult;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;

import java.util.ArrayList;
import java.util.List;

public class RuleEngine {

    private final Board board;
    private final GameMode mode;

    public RuleEngine(Board board, GameMode mode) {
        this.board = board;
        this.mode = mode;
    }

    public MoveResult validateMove(Piece piece, int roll) {
        MoveResult result = new MoveResult();

        if (isPieceAtBase(piece)) {
            return validateBaseMove(piece, roll, result);
        }

        // Entering home straight from standard path (CW pieces only)
        if (isHomeStraightEntry(piece, roll)) {
            if (!canEnterHome(piece)) {
                // Not yet qualified — continue moving normally past approach cell
                int targetCell = calculateTargetCell(piece, roll);
                result.setTargetCell(targetCell);
                if (isOwnPieceAt(piece, targetCell)) {
                    return handleSameColourBlock(result, targetCell, piece);
                }
                if (isOpponentBlockAt(piece, targetCell)) {
                    return handleOpponentBlock(piece, result, targetCell);
                }
                if (isLudoT()) {
                    result = applyLudoTRules(piece, roll, targetCell, result);
                }
                if (isCaptureMove(piece, targetCell)) {
                    return handleCapture(piece, targetCell, result);
                }
                result.setValid(true);
                return result;
            }
            int newPos = calculateHomeStraightPosition(piece, roll);
            if (newPos == GameConstants.HOME_STRAIGHT_SIZE) {
                result.setTargetCell(GameConstants.BOARD_SIZE);
                return handleHomeMove(result);
            }
            result.setValid(true);
            result.setEnteringHomeStraight(true);
            result.setHomeStraightPosition(newPos);
            return result;
        }

        // Already in home straight
        if (piece.isInHomeStraight()) {
            int remaining = GameConstants.HOME_STRAIGHT_SIZE - piece.getHomeStraightPosition();
            if (roll == remaining) {
                if (!canEnterHome(piece)) {
                    result.setValid(false);
                    return result;
                }
                result.setTargetCell(GameConstants.BOARD_SIZE);
                return handleHomeMove(result);
            }
            if (roll < remaining) {
                result.setValid(true);
                result.setMovingInHomeStraight(true);
                result.setHomeStraightPosition(piece.getHomeStraightPosition() + roll);
                return result;
            }
            // roll > remaining — overshoot, invalid
            result.setValid(false);
            return result;
        }

        // CCW home entry handled via approachPassCount
        if (isHomeMove(piece, roll)) {
            if (!canEnterHome(piece)) {
                result.setValid(false);
                return result;
            }
            result.setTargetCell(GameConstants.BOARD_SIZE);
            return handleHomeMove(result);
        }

        int targetCell = calculateTargetCell(piece, roll);
        result.setTargetCell(targetCell);

        // Own pieces at target: block formation or blocked
        if (isOwnPieceAt(piece, targetCell)) {
            return handleSameColourBlock(result, targetCell, piece);
        }

        // Opponent block at target: stop at adjacent cell (T-3)
        if (isOpponentBlockAt(piece, targetCell)) {
            return handleOpponentBlock(piece, result, targetCell);
        }

        if (isLudoT()) {
            result = applyLudoTRules(piece, roll, targetCell, result);
        }

        if (isCaptureMove(piece, targetCell)) {
            return handleCapture(piece, targetCell, result);
        }

        result.setValid(true);
        return result;
    }

    private boolean isPieceAtBase(Piece piece) {
        return piece.getState() == PieceState.BASE;
    }

    private MoveResult validateBaseMove(Piece piece, int roll, MoveResult result) {
        if (roll != GameConstants.MAX_DICE_ROLL) {
            result.setValid(false);
            return result;
        }

        int targetCell = board.getStartX(piece.getColour());
        result.setTargetCell(targetCell);

        // X behaves like any standard cell on arrival
        if (isOwnPieceAt(piece, targetCell)) {
            return handleSameColourBlock(result, targetCell, piece);
        }

        if (isOpponentBlockAt(piece, targetCell)) {
            return handleOpponentBlock(piece, result, targetCell);
        }

        if (isCaptureMove(piece, targetCell)) {
            return handleCapture(piece, targetCell, result);
        }

        result.setValid(true);
        return result;
    }

    // Detects when a CW piece on the standard path will enter the home straight
    public boolean isHomeStraightEntry(Piece piece, int roll) {
        if (piece.isInHomeStraight()) return false;
        if (piece.getDirection() != Direction.CW) return false;
        int approachCell = board.getApproach(piece.getColour());
        int distToApproach = (approachCell - piece.getPosition() + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        return distToApproach <= roll && roll < GameConstants.BOARD_SIZE;
    }

    // How many cells into the home straight this roll takes the piece
    public int calculateHomeStraightPosition(Piece piece, int roll) {
        int approachCell = board.getApproach(piece.getColour());
        int distToApproach = (approachCell - piece.getPosition() + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        int stepsIntoStraight = roll - distToApproach;
        return Math.min(stepsIntoStraight, GameConstants.HOME_STRAIGHT_SIZE);
    }

    // Rule T-1 fix (4.1): CCW subtracts position
    private int calculateTargetCell(Piece piece, int roll) {
        if (piece.getDirection() == Direction.CCW) {
            return (piece.getPosition() - piece.getEffectiveRoll(roll)
                    + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        return (piece.getPosition() + piece.getEffectiveRoll(roll)) % GameConstants.BOARD_SIZE;
    }

    public boolean isOwnPieceAt(Piece piece, int targetCell) {
        List<Piece> piecesAtTarget = board.getPiecesAt(targetCell);
        for (Piece target : piecesAtTarget) {
            if (target.getColour() == piece.getColour()) {
                return true;
            }
        }
        return false;
    }

    // Rule T-4: block formation allowed (1 non-frozen friendly = valid), full block or frozen-only = invalid
    private MoveResult handleSameColourBlock(MoveResult result, int targetCell, Piece piece) {
        long sameColourCount = board.getPiecesAt(targetCell).stream()
                .filter(p -> p.getColour() == piece.getColour()).count();

        long nonFrozenCount = board.getPiecesAt(targetCell).stream()
                .filter(p -> p.getColour() == piece.getColour())
                .filter(p -> p.getActiveEffect() != PieceEffect.FROZEN)
                .count();

        if (sameColourCount >= GameConstants.MIN_BLOCK_SIZE) {
            // Full block already exists — can't join
            result.setValid(false);
            result.setSameColourBlocked(true);
            result.setBlockedAt(targetCell);
        } else if (nonFrozenCount == 0) {
            // Only a frozen piece is there — can't form a block with it
            result.setValid(false);
            result.setSameColourBlocked(true);
            result.setBlockedAt(targetCell);
        } else {
            // Exactly 1 non-frozen friendly piece — valid block formation
            result.setValid(true);
            result.setTargetCell(targetCell);
        }
        return result;
    }

    // Rule T-3: opponent block stops attacker at adjacent cell
    private boolean isOpponentBlockAt(Piece piece, int cell) {
        long opponentCount = board.getPiecesAt(cell).stream()
                .filter(p -> p.getColour() != piece.getColour()).count();
        return opponentCount >= GameConstants.MIN_BLOCK_SIZE;
    }

    private MoveResult handleOpponentBlock(Piece piece, MoveResult result, int blockCell) {
        int adjacentCell;
        if (piece.getDirection() == Direction.CCW) {
            adjacentCell = (blockCell + 1) % GameConstants.BOARD_SIZE;
        } else {
            adjacentCell = (blockCell - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        result.setTargetCell(adjacentCell);
        result.setValid(true);
        result.setBlockedAtAdjacent(true);
        return result;
    }

    private MoveResult applyLudoTRules(Piece piece, int roll, int targetCell, MoveResult result) {
        if (checkMystery(piece, targetCell)) {
            result.setMystery(true);
            result.setTeleportDest(board.getMysteryCell().getDestination());
            result.setLudoTBlocked(false);
        }
        return result;
    }

    private boolean isCaptureMove(Piece piece, int targetCell) {
        Piece target = findCapture(piece, targetCell);
        return target != null;
    }

    private MoveResult handleCapture(Piece piece, int targetCell, MoveResult result) {
        Piece capturedPiece = findCapture(piece, targetCell);
        result.setValid(true);
        result.setCapture(true);
        result.setCapturedPiece(capturedPiece);
        return result;
    }

    // Rule T-1 fix (4.2/4.4): CW uses raw roll (home straight handles approach); CCW uses approachPassCount
    private boolean isHomeMove(Piece piece, int roll) {
        if (piece.isInHomeStraight()) {
            int remaining = GameConstants.HOME_STRAIGHT_SIZE - piece.getHomeStraightPosition();
            return roll == remaining;
        }
        if (piece.getDirection() == Direction.CCW) {
            return isHomeMoveForCCW(piece, roll);
        }
        int startCell = board.getStartX(piece.getColour());
        int distFromStart = (piece.getPosition() - startCell + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        return distFromStart + roll >= GameConstants.BOARD_SIZE;
    }

    // CCW home entry: must have passed approach cell twice, and this move would reach it again
    private boolean isHomeMoveForCCW(Piece piece, int roll) {
        if (piece.getApproachPassCount() < GameConstants.APPROACH_PASS_REQUIRED_CCW) {
            return false;
        }
        int approachCell = board.getApproach(piece.getColour());
        int distToApproach = (piece.getPosition() - approachCell + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        return distToApproach <= piece.getEffectiveRoll(roll);
    }

    private MoveResult handleHomeMove(MoveResult result) {
        result.setValid(true);
        result.setHome(true);
        return result;
    }

    public Piece findCapture(Piece piece, int targetCell) {
        List<Piece> piecesAtTarget = board.getPiecesAt(targetCell);
        for (Piece target : piecesAtTarget) {
            if (isOpponent(piece, target)) {
                return target;
            }
        }
        return null;
    }

    private boolean isOpponent(Piece piece, Piece target) {
        return target.getColour() != piece.getColour();
    }

    public boolean canEnterHome(Piece piece) {
        if (isLudoT()) {
            return hasRequiredCaptures(piece);
        }
        return true;
    }

    private boolean hasRequiredCaptures(Piece piece) {
        return piece.getCaptureCount() >= GameConstants.MIN_CAPTURES_FOR_HOME;
    }

    public boolean checkMystery(Piece piece, int targetCell) {
        if (board.getMysteryCell() == null) {
            return false;
        }
        return targetCell == board.getMysteryPosition();
    }

    // Rule T-4: block movement bypasses getEffectiveRoll; breaks at approach cell if reached
    public BlockMoveResult resolveBlock(Block block, int roll) {
        int steps = roll / block.getSize();
        Direction dir = block.getDirectionForMove();
        int approachCell = board.getApproach(block.getPieces().get(0).getColour());
        boolean breakAtApproach = false;

        for (Piece piece : block.getPieces()) {
            int fromPos = piece.getPosition();
            int newPos;
            if (dir == Direction.CCW) {
                newPos = (fromPos - steps + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
            } else {
                newPos = (fromPos + steps) % GameConstants.BOARD_SIZE;
                int distToApproach = (approachCell - fromPos + GameConstants.BOARD_SIZE)
                        % GameConstants.BOARD_SIZE;
                if (distToApproach <= steps) {
                    breakAtApproach = true;
                }
            }
            if (!breakAtApproach) {
                board.removePiece(piece, fromPos);
                piece.setPosition(newPos);
                board.placePiece(piece, newPos);
            }
        }

        if (breakAtApproach) {
            List<Piece> blockPieces = new ArrayList<>(block.getPieces());
            for (Piece piece : blockPieces) {
                board.removePiece(piece, piece.getPosition());
                piece.setPosition(approachCell);
                board.placePiece(piece, approachCell);
                block.breakBlock(piece);
                piece.clearMovementEffects();
            }
            return new BlockMoveResult(true, approachCell);
        }

        if (!block.getPieces().isEmpty()) {
            block.setPosition(block.getPieces().get(0).getPosition());
        }
        return new BlockMoveResult(false, -1);
    }

    // Fix 4: block-on-block and block-captures-single validation
    public MoveResult validateBlockMove(Block attackerBlock, int roll) {
        MoveResult result = new MoveResult();
        int steps = roll / attackerBlock.getSize();
        Direction dir = attackerBlock.getDirectionForMove();
        Piece leadPiece = attackerBlock.getPieces().get(0);

        int targetCell;
        if (dir == Direction.CCW) {
            targetCell = (leadPiece.getPosition() - steps + GameConstants.BOARD_SIZE)
                    % GameConstants.BOARD_SIZE;
        } else {
            targetCell = (leadPiece.getPosition() + steps) % GameConstants.BOARD_SIZE;
        }
        result.setTargetCell(targetCell);

        long opponentCount = board.getPiecesAt(targetCell).stream()
                .filter(p -> p.getColour() != leadPiece.getColour()).count();

        if (opponentCount >= GameConstants.MIN_BLOCK_SIZE) {
            Block defenderBlock = buildBlockAt(targetCell, leadPiece.getColour());
            if (defenderBlock.canBeCaptured(attackerBlock)) {
                result.setValid(true);
                result.setBlockCapture(true);
                result.setDefenderBlock(defenderBlock);
            } else {
                // Attacker block smaller — stop at adjacent cell
                int adjacentCell = (targetCell - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
                result.setTargetCell(adjacentCell);
                result.setValid(true);
                result.setBlockedAtAdjacent(true);
            }
            return result;
        }

        // Single opponent piece at target — block captures it
        if (isCaptureMove(leadPiece, targetCell)) {
            result.setValid(true);
            result.setCapture(true);
            result.setCapturedPiece(findCapture(leadPiece, targetCell));
            return result;
        }

        result.setValid(true);
        return result;
    }

    private Block buildBlockAt(int cell, Colour attackerColour) {
        Block block = new Block(cell, Direction.CW);
        for (Piece p : board.getPiecesAt(cell)) {
            if (p.getColour() != attackerColour) {
                block.addPiece(p);
            }
        }
        return block;
    }

    public void applyBlockCapture(Block attacker, Block defender) {
        if (defender.canBeCaptured(attacker)) {
            for (Piece p : new ArrayList<>(defender.getPieces())) {
                board.removePiece(p, p.getPosition());
            }
            resetAllPieces(defender.getPieces());
            incrementCaptureCount(attacker.getPieces());
        }
    }

    private void resetAllPieces(List<Piece> pieces) {
        for (Piece piece : pieces) {
            piece.reset();
        }
    }

    private void incrementCaptureCount(List<Piece> pieces) {
        for (Piece piece : pieces) {
            piece.capture();
        }
    }

    // Rule T-4 fix (4.5): resolve block direction from the piece with greater distance to home
    public Direction resolveBlockDirection(Piece p1, Piece p2, Board board) {
        int dist1 = board.distanceToHome(p1);
        int dist2 = board.distanceToHome(p2);
        return dist1 >= dist2 ? p1.getDirection() : p2.getDirection();
    }

    private boolean isLudoT() {
        return mode == GameMode.LUDO_T;
    }
}
