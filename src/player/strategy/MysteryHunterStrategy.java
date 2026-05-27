package player.strategy;

import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.AbstractPlayer;

/**
 * Blue strategy (spec 2.1.4):
 * - CCW pieces actively seek the mystery cell.
 * - CW pieces actively avoid the mystery cell.
 * - Board wrap-around handled correctly for CCW heading-towards check.
 */
public class MysteryHunterStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;
    private int cycleIndex;

    public MysteryHunterStrategy(AbstractPlayer player) {
        this.player = player;
        this.cycleIndex = 0;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        Piece nextPiece = getNextCyclePiece();
        if (nextPiece.isNull()) {
            return NoPiece.getInstance();
        }

        // CCW: prioritise heading towards the mystery cell
        if (targetsMystery(nextPiece, board)) {
            return nextPiece;
        }

        // CW: skip this piece if it would land on the mystery cell, try the next in cycle
        if (isAvoidingMystery(nextPiece, roll, board)) {
            Piece altPiece = getNextCyclePiece();
            if (!altPiece.isNull()) {
                return altPiece;
            }
        }

        return getNextMovablePiece(board);
    }

    private Piece getNextCyclePiece() {
        Piece[] pieces = player.getPieces();
        for (int i = 0; i < GameConstants.NUM_PIECES; i++) {
            int index = (cycleIndex + i) % GameConstants.NUM_PIECES;
            Piece piece = pieces[index];
            if (piece.getState() == PieceState.ACTIVE) {
                cycleIndex = (index + 1) % GameConstants.NUM_PIECES;
                return piece;
            }
        }
        return NoPiece.getInstance();
    }

    /** CCW piece is targeting mystery if it is heading towards the mystery cell. */
    private boolean targetsMystery(Piece piece, Board board) {
        return piece.getDirection() == Direction.CCW
                && isHeadingTowardsMystery(piece, board);
    }

    /**
     * Fix (9): correct wrap-around for CCW pieces.
     * A CCW piece is "heading towards" the mystery cell if the mystery cell lies
     * in the forward half of the CCW direction (CCW distance <= BOARD_SIZE/2).
     */
    private boolean isHeadingTowardsMystery(Piece piece, Board board) {
        int mysteryPosition = board.getMysteryPosition();
        if (mysteryPosition == GameConstants.NO_POSITION) {
            return false;
        }
        // CCW distance from piece to mystery cell
        int ccwDist = (piece.getPosition() - mysteryPosition + GameConstants.BOARD_SIZE)
                % GameConstants.BOARD_SIZE;
        return ccwDist > 0 && ccwDist <= GameConstants.BOARD_SIZE / 2;
    }

    /**
     * CW piece should avoid landing on the mystery cell.
     * Returns true if the CW roll would place the piece exactly on the mystery cell.
     */
    private boolean isAvoidingMystery(Piece piece, int roll, Board board) {
        int mysteryPosition = board.getMysteryPosition();
        if (mysteryPosition == GameConstants.NO_POSITION) {
            return false;
        }
        if (piece.getDirection() != Direction.CW) {
            return false;
        }
        int target = (piece.getPosition() + piece.getEffectiveRoll(roll)) % GameConstants.BOARD_SIZE;
        return target == mysteryPosition;
    }

    private Piece getNextMovablePiece(Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (piece.getPosition() != board.getMysteryPosition()) {
                return piece;
            }
        }
        return NoPiece.getInstance();
    }
}
