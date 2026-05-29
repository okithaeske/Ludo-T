package player.strategy;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

/**
 * Green strategy (spec 2.1.2):
 * - Roll 6: if bringing a base piece to X would land on a friendly (forming a block), prefer that.
 *   Otherwise still move the base piece to X.
 * - Capture only with pieces that still need captures to qualify for home entry.
 * - Never break a block unless no other active piece ahead of the block (closer to home) can move.
 */
public class BlockerStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public BlockerStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        // Roll 6: bring base piece — prioritise if it would form a block at X
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        // Form a block with board pieces if possible (only if block-moving is NOT preferred below)
        if (canFormBlock(roll, board)) {
            return getPieceToFormBlock(roll, board);
        }

        // Move an existing block only if no non-block piece closer to home can move
        if (hasBlockOnBoard(board)) {
            if (!canPieceAheadOfBlockMove(roll, board)) {
                return getBlockPiece(board);
            }
        }

        // Seek capture only for pieces that still need it for home entry
        Piece capturePiece = findCaptureForHome(roll, board);
        if (!capturePiece.isNull()) {
            return capturePiece;
        }

        return avoidCapture(board);
    }

    private boolean canFormBlock(int roll, Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return true;
            }
        }
        return false;
    }

    private boolean pieceCanFormBlock(Piece piece, int roll, Board board) {
        int targetCell = board.projectPosition(piece, piece.getEffectiveRoll(roll));
        return hasFriendlyPieceAt(board.getPiecesAt(targetCell), piece.getColour());
    }

    private boolean hasFriendlyPieceAt(List<Piece> pieces, Colour colour) {
        for (Piece target : pieces) {
            if (target.getColour() == colour) {
                return true;
            }
        }
        return false;
    }

    private Piece getPieceToFormBlock(int roll, Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return piece;
            }
        }
        return NoPiece.getInstance();
    }

    private boolean hasBlockOnBoard(Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if at least one non-block piece that is closer to home (further advanced)
     * than the block can make a valid move with the current roll.
     */
    private boolean canPieceAheadOfBlockMove(int roll, Board board) {
        int blockDist = getBlockDistanceToHome(board);
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            // Skip pieces that are part of the block
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                continue;
            }
            // Only consider pieces ahead of (closer to home than) the block
            int pieceDist = board.distanceToHome(piece);
            if (pieceDist < blockDist) {
                return true; // at least one piece ahead can move
            }
        }
        return false;
    }

    private int getBlockDistanceToHome(Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                return board.distanceToHome(piece);
            }
        }
        return Integer.MAX_VALUE;
    }

    private Piece getBlockPiece(Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                return piece;
            }
        }
        return NoPiece.getInstance();
    }

    // Only seek captures for pieces that still need them to qualify for home entry
    private Piece findCaptureForHome(int roll, Board board) {
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            if (piece.getCaptureCount() >= GameConstants.MIN_CAPTURES_FOR_HOME) {
                continue;
            }
            if (player.canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return NoPiece.getInstance();
    }

    private Piece avoidCapture(Board board) {
        Piece leastAdvanced = NoPiece.getInstance();
        int maxDistanceToHome = 0;
        for (Piece piece : player.getMovablePiecesOnBoard()) {
            int distance = board.distanceToHome(piece);
            if (distance > maxDistanceToHome) {
                maxDistanceToHome = distance;
                leastAdvanced = piece;
            }
        }
        return leastAdvanced;
    }
}
