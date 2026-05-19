package player.strategy;

import model.Board;
import model.GameConstants;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

public class AggressiveStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public AggressiveStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        Piece capturablePiece = findCapturableTarget(roll, board);
        if (capturablePiece != null) {
            return capturablePiece;
        }

        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        return player.getPieceClosestToHome(board);
    }

    private Piece findCapturableTarget(int roll, Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            int targetCell = piece.getPosition() + piece.getEffectiveRoll(roll);
            if (hasOpponentAt(targetCell, board, piece)) {
                return piece;
            }
        }
        return null;
    }

    private boolean hasOpponentAt(int targetCell, Board board, Piece mover) {
        List<Piece> piecesAtTarget = board.getPiecesAt(targetCell);
        for (Piece target : piecesAtTarget) {
            if (target.getColour() != mover.getColour()) {
                return true;
            }
        }
        return false;
    }
}