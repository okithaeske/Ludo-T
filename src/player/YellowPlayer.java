package player;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.Piece;

import java.util.List;

public class YellowPlayer extends AbstractPlayer {

    public YellowPlayer() {
        super(Colour.YELLOW, "Yellow");
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && hasPiecesAtBase()) {
            return getPiecesAtBase().get(0);
        }

        if (needsCaptureForHome() && canCapture(roll, board)) {
            return findCaptureablePiece(roll, board);
        }

        return getPieceClosestToHome(board);
    }

    private boolean needsCaptureForHome() {
        for (Piece piece : getPiecesOnBoard()) {
            if (pieceNeedsCapture(piece)) {
                return true;
            }
        }
        return false;
    }

    private boolean pieceNeedsCapture(Piece piece) {
        return piece.getCaptureCount() < GameConstants.MIN_CAPTURES_FOR_HOME;
    }

    private boolean canCapture(int roll, Board board) {
        return findCaptureablePiece(roll, board) != null;
    }

    private Piece findCaptureablePiece(int roll, Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }




}