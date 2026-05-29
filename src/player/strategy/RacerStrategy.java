package player.strategy;

import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.AbstractPlayer;

/**
 * Yellow strategy (spec 2.1.3):
 * - Always moves a piece from base when rolling 6 if base pieces exist — no exceptions.
 * - Capture only with pieces that still need captures for home entry.
 * - Fallback: move the piece closest to home.
 */
public class RacerStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public RacerStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        // Unconditionally move from base when rolling 6 and base pieces exist
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        // Capture only with pieces that still need a capture to qualify for home
        if (canCaptureWithNeedyPiece(roll, board)) {
            return findCaptureablePiece(roll, board);
        }

        return player.getPieceClosestToHome(board);
    }

    private boolean canCaptureWithNeedyPiece(int roll, Board board) {
        return !findCaptureablePiece(roll, board).isNull();
    }

    private Piece findCaptureablePiece(int roll, Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            // Only consider pieces that still need a capture
            if (piece.getCaptureCount() < GameConstants.MIN_CAPTURES_FOR_HOME
                    && player.canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return NoPiece.getInstance();
    }
}
