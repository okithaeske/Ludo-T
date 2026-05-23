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

        if (isOwnPieceAt(piece, targetCell)) {
            return handleSameColourBlock(result, targetCell);
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

    private int calculateTargetCell(Piece piece, int roll) {
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

    private MoveResult handleSameColourBlock(MoveResult result, int targetCell) {
        result.setValid(false);
        result.setSameColourBlocked(true);
        result.setBlockedAt(targetCell);
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

    // A piece goes home when its total travel distance from its colour's start cell
    // reaches or exceeds BOARD_SIZE.
    private boolean isHomeMove(Piece piece, int roll) {
        int startCell = board.getStartX(piece.getColour());
        int distFromStart = (piece.getPosition() - startCell + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        return distFromStart + piece.getEffectiveRoll(roll) >= GameConstants.BOARD_SIZE;
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

    private boolean hasPassedApproach(Piece piece) {
        return piece.getPosition() >= board.getApproach(piece.getColour());
    }

    public boolean checkMystery(Piece piece, int targetCell) {
        if (board.getMysteryCell() == null) {
            return false;
        }
        return targetCell == board.getMysteryPosition();
    }

    public void resolveBlock(Block block, int roll) {
        int steps = roll / block.getSize();
        for (Piece piece : block.getPieces()) {
            piece.move(steps);
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

    private boolean isLudoT() {
        return mode == GameMode.LUDO_T;
    }
}