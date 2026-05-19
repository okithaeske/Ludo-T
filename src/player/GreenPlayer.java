package player;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.Piece;

import java.util.List;

public class GreenPlayer extends AbstractPlayer {

    public GreenPlayer() {
        super(Colour.GREEN, "Green");
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && hasPiecesAtBase()) {
            return getPiecesAtBase().get(0);
        }

        if (canFormBlock(roll, board)) {
            return getPieceToFormBlock(roll, board);
        }

        if (hasBlockOnBoard(board)) {
            return getBlockPiece(board);
        }

        Piece capturePiece = findCaptureForHome(roll, board);
        if (capturePiece != null) {
            return capturePiece;
        }

        return avoidCapture(board);
    }

    private boolean canFormBlock(int roll, Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return true;
            }
        }
        return false;
    }

    private boolean pieceCanFormBlock(Piece piece, int roll, Board board) {
        int targetCell = piece.getPosition() + roll;
        return hasFriendlyPieceAt(board.getPiecesAt(targetCell));
    }

    private boolean hasFriendlyPieceAt(List<Piece> piecesAtTarget) {
        for (Piece target : piecesAtTarget) {
            if (target.getColour() == this.colour) {
                return true;
            }
        }
        return false;
    }

    private Piece getPieceToFormBlock(int roll, Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }

    private boolean hasBlockOnBoard(Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (isPartOfBlock(piece, board)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPartOfBlock(Piece piece, Board board) {
        return board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE;
    }

    private Piece getBlockPiece(Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (isPartOfBlock(piece, board)) {
                return piece;
            }
        }
        return null;
    }

    private Piece findCaptureForHome(int roll, Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (doesNotNeedCaptureForHome(piece)) {
                continue;
            }
            if (canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }




    private boolean doesNotNeedCaptureForHome(Piece piece) {
        return piece.getCaptureCount() >= GameConstants.MIN_CAPTURES_FOR_HOME;
    }

    private Piece avoidCapture(Board board) {
        Piece leastAdvanced = null;
        int maxDistanceToHome = 0;
        for (Piece piece : getPiecesOnBoard()) {
            int distance = board.distanceToHome(piece);
            if (distance > maxDistanceToHome) {
                maxDistanceToHome = distance;
                leastAdvanced = piece;
            }
        }
        return leastAdvanced;
    }


}