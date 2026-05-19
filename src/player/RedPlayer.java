package player;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.Piece;

import java.util.List;

public class RedPlayer extends AbstractPlayer {

    public RedPlayer() {
        super(Colour.RED, "Red");
    }

    // Aggressive Strategy: Prioritise capturing opponent pieces, then moving pieces closest to home,and finally bringing new pieces onto the board.
    @Override
    public Piece choosePiece(int roll, Board board) {

        Piece capturablePiece  = findCapturableTarget(roll, board);
        if (capturablePiece != null) {
            return capturablePiece;
        }

        if (roll == 6 && hasPiecesAtBase()) {
            return getPiecesAtBase().get(0);
        }

        return getPieceClosestToHome(board);

    }

    private Piece findCapturableTarget(int roll, Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            int targetcell = piece.getPosition() + piece.getEffectiveRoll(roll);
            Piece capturedPiece = findOpponentAt(targetcell, board);
            if (capturedPiece != null) {
                return piece;
            }
        }
        return null;

    }

    private Piece findOpponentAt(int targetCell, Board board) {
        List<Piece> piecesAtTarget = board.getPiecesAt(targetCell);
        for (Piece target : piecesAtTarget) {
            if (target.getColour() != this.colour) {
                return target;
            }
        }
        return null;
    }



}
