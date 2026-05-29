package engine;

import enums.Colour;
import enums.Direction;
import enums.GameMode;
import enums.PieceState;
import model.Block;
import model.BlockMoveResult;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;

import java.util.ArrayList;
import java.util.List;

/** Stateless move validation and block resolution. */
public class RuleEngine {

    private final Board board;
    private final GameMode mode;

    public RuleEngine(Board board, GameMode mode) {
        this.board = board;
        this.mode = mode;
    }

    public MoveResult validateMove(Piece piece, int roll) {
        MoveResult result = new MoveResult();

        if (piece.isMovementRestricted() || piece.getState() == PieceState.HOME) {
            result.setValid(false);
            return result;
        }

        if (isPieceAtBase(piece)) {
            return validateBaseMove(piece, roll, result);
        }

        int steps = piece.getEffectiveRoll(roll);
        if (steps <= 0) {
            result.setValid(false);
            return result;
        }

        if (piece.isInHomeStraight()) {
            return validateHomeStraightMove(piece, steps, result);
        }

        MoveResult homePathResult = tryEnterHomeStraight(piece, steps, result);
        if (homePathResult != null) {
            return homePathResult;
        }

        int blockingCell = findFirstOpponentBlockInPath(piece, steps);
        if (blockingCell != GameConstants.NO_POSITION) {
            return handleOpponentBlock(piece, result, blockingCell);
        }

        int targetCell = calculateTargetCell(piece, steps);
        result.setTargetCell(targetCell);

        if (isOwnPieceAt(piece, targetCell)) {
            return handleSameColourBlock(result, targetCell, piece);
        }

        if (isLudoT()) {
            result = applyLudoTRules(piece, targetCell, result);
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

        if (isOpponentBlockAt(piece, targetCell)) {
            result.setValid(false);
            result.setBlockedAt(targetCell);
            return result;
        }

        if (isOwnPieceAt(piece, targetCell)) {
            return handleSameColourBlock(result, targetCell, piece);
        }

        if (isCaptureMove(piece, targetCell)) {
            return handleCapture(piece, targetCell, result);
        }

        result.setValid(true);
        return result;
    }

    private MoveResult validateHomeStraightMove(Piece piece, int steps, MoveResult result) {
        int newHomePos = piece.getHomeStraightPosition() + steps;
        if (newHomePos < GameConstants.HOME_EXIT_DISTANCE) {
            result.setValid(true);
            result.setMovingInHomeStraight(true);
            result.setHomeStraightPosition(newHomePos);
            return result;
        }
        if (newHomePos == GameConstants.HOME_EXIT_DISTANCE) {
            return handleHomeMove(result);
        }
        result.setValid(false);
        return result;
    }

    private MoveResult tryEnterHomeStraight(Piece piece, int steps, MoveResult result) {
        int distToApproach = distanceToApproach(piece);

        // If the piece only lands on the approach, it remains on the standard path.
        if (steps <= distToApproach) {
            return null;
        }

        if (!canEnterHome(piece)) {
            return null;
        }

        if (piece.getDirection() == Direction.CCW) {
            int passesAfterThisMove = piece.getApproachPassCount() + 1;
            if (passesAfterThisMove < GameConstants.APPROACH_PASS_REQUIRED_CCW) {
                return null;
            }
        }

        int stepsIntoHomeStraight = steps - distToApproach;
        if (stepsIntoHomeStraight < GameConstants.HOME_EXIT_DISTANCE) {
            result.setValid(true);
            result.setEnteringHomeStraight(true);
            result.setHomeStraightPosition(stepsIntoHomeStraight);
            return result;
        }
        if (stepsIntoHomeStraight == GameConstants.HOME_EXIT_DISTANCE) {
            return handleHomeMove(result);
        }
        result.setValid(false);
        return result;
    }

    private int distanceToApproach(Piece piece) {
        int approachCell = board.getApproach(piece.getColour());
        if (piece.getDirection() == Direction.CCW) {
            return (piece.getPosition() - approachCell + GameConstants.BOARD_SIZE)
                    % GameConstants.BOARD_SIZE;
        }
        return (approachCell - piece.getPosition() + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
    }

    private int calculateTargetCell(Piece piece, int steps) {
        if (piece.getDirection() == Direction.CCW) {
            return (piece.getPosition() - steps + GameConstants.BOARD_SIZE)
                    % GameConstants.BOARD_SIZE;
        }
        return (piece.getPosition() + steps) % GameConstants.BOARD_SIZE;
    }

    private int calculateCellAfterSteps(int position, Direction direction, int steps) {
        if (direction == Direction.CCW) {
            return (position - steps + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        return (position + steps) % GameConstants.BOARD_SIZE;
    }

    private int findFirstOpponentBlockInPath(Piece piece, int steps) {
        for (int step = 1; step <= steps; step++) {
            int cell = calculateCellAfterSteps(piece.getPosition(), piece.getDirection(), step);
            if (isOpponentBlockAt(piece, cell)) {
                return cell;
            }
        }
        return GameConstants.NO_POSITION;
    }

    public boolean isOwnPieceAt(Piece piece, int targetCell) {
        for (Piece target : board.getPiecesAt(targetCell)) {
            if (target.getColour() == piece.getColour()) {
                return true;
            }
        }
        return false;
    }

    private MoveResult handleSameColourBlock(MoveResult result, int targetCell, Piece piece) {
        result.setValid(true);
        result.setTargetCell(targetCell);
        return result;
    }

    private boolean isOpponentBlockAt(Piece piece, int cell) {
        long opponentCount = board.getPiecesAt(cell).stream()
                .filter(p -> p.getColour() != piece.getColour()).count();
        return opponentCount >= GameConstants.MIN_BLOCK_SIZE;
    }

    private MoveResult handleOpponentBlock(Piece piece, MoveResult result, int blockCell) {
        int adjacentCell = board.getAdjacentCell(blockCell, piece.getDirection());
        result.setTargetCell(adjacentCell);
        result.setValid(adjacentCell != piece.getPosition());
        result.setBlockedAtAdjacent(true);
        result.setBlockedAt(blockCell);
        return result;
    }

    private MoveResult applyLudoTRules(Piece piece, int targetCell, MoveResult result) {
        if (checkMystery(piece, targetCell)) {
            result.setMystery(true);
            result.setTeleportDest(board.getMysteryCell().getDestination());
            result.setLudoTBlocked(false);
        }
        return result;
    }

    private boolean isCaptureMove(Piece piece, int targetCell) {
        return findCapture(piece, targetCell) != null;
    }

    private MoveResult handleCapture(Piece piece, int targetCell, MoveResult result) {
        Piece capturedPiece = findCapture(piece, targetCell);
        result.setValid(true);
        result.setTargetCell(targetCell);
        result.setCapture(true);
        result.setCapturedPiece(capturedPiece);
        return result;
    }

    private MoveResult handleHomeMove(MoveResult result) {
        result.setValid(true);
        result.setHome(true);
        return result;
    }

    public Piece findCapture(Piece piece, int targetCell) {
        for (Piece target : board.getPiecesAt(targetCell)) {
            if (target.getColour() != piece.getColour()) {
                return target;
            }
        }
        return null;
    }

    public boolean canEnterHome(Piece piece) {
        if (isLudoT()) {
            return piece.getCaptureCount() >= GameConstants.MIN_CAPTURES_FOR_HOME;
        }
        return true;
    }

    public boolean checkMystery(Piece piece, int targetCell) {
        return board.getMysteryCell() != null
                && board.getMysteryCell().isActive()
                && targetCell == board.getMysteryPosition();
    }

    public BlockMoveResult resolveBlock(Block block, int roll) {
        int steps = roll / block.getSize();
        Direction dir = block.getDirectionForMove();
        int approachCell = board.getApproach(block.getPieces().get(0).getColour());
        boolean breakAtApproach = false;

        for (Piece piece : block.getPieces()) {
            int fromPos = piece.getPosition();
            if (dir == Direction.CW) {
                int distToApproach = (approachCell - fromPos + GameConstants.BOARD_SIZE)
                        % GameConstants.BOARD_SIZE;
                if (steps > distToApproach) {
                    breakAtApproach = true;
                }
            } else {
                int distToApproach = (fromPos - approachCell + GameConstants.BOARD_SIZE)
                        % GameConstants.BOARD_SIZE;
                if (steps > distToApproach) {
                    breakAtApproach = true;
                }
            }
        }

        if (breakAtApproach) {
            List<Piece> blockPieces = new ArrayList<>(block.getPieces());
            for (Piece piece : blockPieces) {
                board.removePiece(piece, piece.getPosition());
                piece.moveTo(approachCell);
                board.placePiece(piece, approachCell);
                block.breakBlock(piece);
                piece.clearMovementEffects();
            }
            return new BlockMoveResult(true, approachCell);
        }

        for (Piece piece : new ArrayList<>(block.getPieces())) {
            int fromPos = piece.getPosition();
            int newPos = calculateCellAfterSteps(fromPos, dir, steps);
            board.removePiece(piece, fromPos);
            piece.moveTo(newPos);
            board.placePiece(piece, newPos);
        }

        if (!block.getPieces().isEmpty()) {
            block.setPosition(block.getPieces().get(0).getPosition());
        }
        return new BlockMoveResult(false, -1);
    }

    public MoveResult validateBlockMove(Block attackerBlock, int roll) {
        MoveResult result = new MoveResult();
        int steps = roll / attackerBlock.getSize();
        if (steps <= 0) {
            result.setValid(false);
            return result;
        }

        Direction dir = attackerBlock.getDirectionForMove();
        Piece leadPiece = attackerBlock.getPieces().get(0);
        int targetCell = calculateCellAfterSteps(leadPiece.getPosition(), dir, steps);
        result.setTargetCell(targetCell);

        int firstOpponentBlock = findFirstOpponentBlockInPathForBlock(attackerBlock, steps);
        if (firstOpponentBlock != GameConstants.NO_POSITION) {
            Colour defenderColour = firstOpponentColourAt(firstOpponentBlock, leadPiece.getColour());
            Block defenderBlock = board.getBlockAt(firstOpponentBlock, defenderColour, dir);
            if (firstOpponentBlock == targetCell
                    && defenderBlock != null
                    && defenderBlock.canBeCaptured(attackerBlock)) {
                result.setValid(true);
                result.setBlockCapture(true);
                result.setDefenderBlock(defenderBlock);
                return result;
            }

            result.setTargetCell(board.getAdjacentCell(firstOpponentBlock, dir));
            result.setValid(true);
            result.setBlockedAtAdjacent(true);
            result.setBlockedAt(firstOpponentBlock);
            return result;
        }

        if (isCaptureMove(leadPiece, targetCell)) {
            result.setValid(true);
            result.setCapture(true);
            result.setCapturedPiece(findCapture(leadPiece, targetCell));
            return result;
        }

        result.setValid(true);
        return result;
    }

    private int findFirstOpponentBlockInPathForBlock(Block block, int steps) {
        Piece leadPiece = block.getPieces().get(0);
        Direction dir = block.getDirectionForMove();
        for (int step = 1; step <= steps; step++) {
            int cell = calculateCellAfterSteps(leadPiece.getPosition(), dir, step);
            long opponentCount = board.getPiecesAt(cell).stream()
                    .filter(p -> p.getColour() != leadPiece.getColour())
                    .count();
            if (opponentCount >= GameConstants.MIN_BLOCK_SIZE) {
                return cell;
            }
        }
        return GameConstants.NO_POSITION;
    }

    private Colour firstOpponentColourAt(int cell, Colour attackerColour) {
        return board.getPiecesAt(cell).stream()
                .filter(p -> p.getColour() != attackerColour)
                .map(Piece::getColour)
                .findFirst()
                .orElseThrow();
    }

    public void applyBlockCapture(Block attacker, Block defender) {
        if (defender.canBeCaptured(attacker)) {
            List<Piece> captured = new ArrayList<>(defender.getPieces());
            for (Piece p : captured) {
                board.removePiece(p, p.getPosition());
            }
            for (Piece p : captured) {
                p.reset();
            }
            for (Piece piece : attacker.getPieces()) {
                piece.capture();
            }
        }
    }

    public Direction resolveBlockDirection(Piece p1, Piece p2, Board board) {
        int dist1 = board.distanceToHome(p1);
        int dist2 = board.distanceToHome(p2);
        return dist1 >= dist2 ? p1.getDirection() : p2.getDirection();
    }

    private boolean isLudoT() {
        return mode == GameMode.LUDO_T;
    }
}
