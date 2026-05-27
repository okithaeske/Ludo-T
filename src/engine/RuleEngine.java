package engine;

import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import model.Block;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;

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
        if (roll == GameConstants.MAX_DICE_ROLL) {
            result.setValid(true);
            result.setTargetCell(board.getStartX(piece.getColour()));
        } else {
            result.setValid(false);
        }
        return result;
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

    // Rule T-4 fix (4.5): allow block formation (1 friendly at target = valid),
    // prevent joining an existing full block (2+ = invalid)
    private MoveResult handleSameColourBlock(MoveResult result, int targetCell, Piece piece) {
        long sameColourCount = board.getPiecesAt(targetCell).stream()
                .filter(p -> p.getColour() == piece.getColour()).count();
        if (sameColourCount >= GameConstants.MIN_BLOCK_SIZE) {
            // Target already has a full block — can't join
            result.setValid(false);
            result.setSameColourBlocked(true);
            result.setBlockedAt(targetCell);
        } else {
            // Exactly 1 friendly piece — form a block
            result.setValid(true);
            result.setTargetCell(targetCell);
        }
        return result;
    }

    // Rule T-3 fix (4.3): opponent block stops attacker at adjacent cell
    private boolean isOpponentBlockAt(Piece piece, int cell) {
        long opponentCount = board.getPiecesAt(cell).stream()
                .filter(p -> p.getColour() != piece.getColour()).count();
        return opponentCount >= GameConstants.MIN_BLOCK_SIZE;
    }

    private MoveResult handleOpponentBlock(Piece piece, MoveResult result, int blockCell) {
        int adjacentCell;
        if (piece.getDirection() == Direction.CCW) {
            // CCW: one cell after the block (higher index = behind in CCW travel)
            adjacentCell = (blockCell + 1) % GameConstants.BOARD_SIZE;
        } else {
            // CW: one cell before the block
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

    // Rule T-1 fix (4.2/4.4): separate CW and CCW home-move detection
    private boolean isHomeMove(Piece piece, int roll) {
        if (piece.getDirection() == Direction.CCW) {
            return isHomeMoveForCCW(piece, roll);
        }
        // CW: travelled enough from start cell
        int startCell = board.getStartX(piece.getColour());
        int distFromStart = (piece.getPosition() - startCell + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        return distFromStart + piece.getEffectiveRoll(roll) >= GameConstants.BOARD_SIZE;
    }

    // CCW home entry: must have passed approach cell twice, and this move would reach it again
    private boolean isHomeMoveForCCW(Piece piece, int roll) {
        if (piece.getApproachPassCount() < GameConstants.APPROACH_PASS_REQUIRED_CCW) {
            return false;
        }
        int approachCell = board.getApproach(piece.getColour());
        // CCW distance from current position to approach cell
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

    // Rule T-4 fix (4.6): block movement bypasses getEffectiveRoll; updates board positions
    public void resolveBlock(Block block, int roll) {
        int steps = roll / block.getSize();
        Direction dir = block.getDirectionForMove();
        for (Piece piece : block.getPieces()) {
            int fromPos = piece.getPosition();
            int newPos;
            if (dir == Direction.CCW) {
                newPos = (fromPos - steps + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
            } else {
                newPos = (fromPos + steps) % GameConstants.BOARD_SIZE;
            }
            board.removePiece(piece, fromPos);
            piece.setPosition(newPos);
            board.placePiece(piece, newPos);
        }
        if (!block.getPieces().isEmpty()) {
            block.setPosition(block.getPieces().get(0).getPosition());
        }
    }

    public void applyBlockCapture(Block attacker, Block defender) {
        if (attacker.canBeCaptured(defender)) {
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
