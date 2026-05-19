package player.strategy;

import model.Board;
import model.GameConstants;
import model.Piece;
import player.AbstractPlayer;

public class RacerStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public RacerStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        if (needsCaptureForHome() && canCapture(roll, board)) {
            return findCaptureablePiece(roll, board);
        }

        return player.getPieceClosestToHome(board);
    }

    private boolean needsCaptureForHome() {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (piece.getCaptureCount() < GameConstants.MIN_CAPTURES_FOR_HOME) {
                return true;
            }
        }
        return false;
    }

    private boolean canCapture(int roll, Board board) {
        return findCaptureablePiece(roll, board) != null;
    }

    private Piece findCaptureablePiece(int roll, Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (player.canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }
}