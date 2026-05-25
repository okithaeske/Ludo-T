package player.strategy;

import enums.Direction;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.AbstractPlayer;

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
        if (nextPiece == null) {
            return NoPiece.getInstance();
        }

        if (targetsMystery(nextPiece, board)) {
            return nextPiece;
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

    private boolean targetsMystery(Piece piece, Board board) {
        return piece.getDirection() == Direction.CCW
                && isHeadingTowardsMystery(piece, board);
    }

    private boolean isHeadingTowardsMystery(Piece piece, Board board) {
        int mysteryPosition = board.getMysteryPosition();
        if (mysteryPosition == GameConstants.NO_POSITION) {
            return false;
        }
        return piece.getPosition() < mysteryPosition;
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