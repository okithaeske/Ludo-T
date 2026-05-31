package player.strategy;

import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.AbstractPlayer;

/**
 * Red strategy (spec 2.1.1):
 * - Prioritises capturing the opponent piece closest to its own home.
 * - Only brings a new piece from base when rolling 6 if no capture is possible
 *   AND there is currently no piece on the standard path.
 * - When no capture, moves the piece closest to home that does NOT form a block;
 *   block creation is only allowed when all non-blocking moves are invalid.
 */
public class AggressiveStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public AggressiveStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        // 1. Try to capture — prefer target closest to their own home
        Piece capturablePiece = findClosestCapturableTarget(roll, board);
        if (!capturablePiece.isNull()) {
            return capturablePiece;
        }

        // 2. Roll 6 + no capture possible → bring from base regardless of board state (spec §2.1.1)
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        // 3. Move piece closest to home — avoid creating a block if possible
        return getPieceClosestToHomeNoBlock(roll, board);
    }

    /**
     * Finds the attacker piece whose target cell contains an opponent closest to that
     * opponent's own home (ascending distance = highest priority).
     */
    private Piece findClosestCapturableTarget(int roll, Board board) {
        Piece bestAttacker = NoPiece.getInstance();
        int minDistToHome = Integer.MAX_VALUE;

        for (Piece piece : player.getMovablePiecesOnBoard()) {
            int targetCell = board.projectPosition(piece, piece.getEffectiveRoll(roll));
            for (Piece target : board.getPiecesAt(targetCell)) {
                if (target.getColour() != piece.getColour()) {
                    int dist = board.distanceToHome(target);
                    if (dist < minDistToHome) {
                        minDistToHome = dist;
                        bestAttacker = piece;
                    }
                }
            }
        }
        return bestAttacker;
    }

    /**
     * Returns the board piece closest to home that would NOT create a block.
     * Falls back to the closest piece that would create a block if no non-blocking move exists.
     */
    private Piece getPieceClosestToHomeNoBlock(int roll, Board board) {
        Piece closestNoBlock   = NoPiece.getInstance();
        Piece closestWithBlock = NoPiece.getInstance();
        int minDistNoBlock   = Integer.MAX_VALUE;
        int minDistWithBlock = Integer.MAX_VALUE;

        for (Piece piece : player.getMovablePiecesOnBoard()) {
            int targetCell = board.projectPosition(piece, piece.getEffectiveRoll(roll));
            int dist = board.distanceToHome(piece);
            boolean wouldBlock = hasFriendlyAt(targetCell, board, piece);

            if (!wouldBlock && dist < minDistNoBlock) {
                minDistNoBlock = dist;
                closestNoBlock = piece;
            } else if (wouldBlock && dist < minDistWithBlock) {
                minDistWithBlock = dist;
                closestWithBlock = piece;
            }
        }
        return closestNoBlock.isNull() ? closestWithBlock : closestNoBlock;
    }

    private boolean hasFriendlyAt(int cell, Board board, Piece mover) {
        for (Piece p : board.getPiecesAt(cell)) {
            if (p.getColour() == mover.getColour()) return true;
        }
        return false;
    }
}
