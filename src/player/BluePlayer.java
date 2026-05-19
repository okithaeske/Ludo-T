package player;

import enums.Colour;
import enums.Direction;
import model.Board;
import model.GameConstants;
import model.Piece;

public class BluePlayer extends AbstractPlayer {

    private int cycleIndex;

    public BluePlayer() {
        super(Colour.BLUE, "Blue");
        this.cycleIndex = 0;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && hasPiecesAtBase()) {
            return getPiecesAtBase().get(0);
        }

        Piece nextPiece = getNextCyclePiece();
        if (nextPiece == null) {
            return null;
        }

        if (targetsMystery(nextPiece, board)) {
            return nextPiece;
        }

        return getNextMovablePiece(board);
    }

    private Piece getNextCyclePiece() {
        for (int i = 0; i < GameConstants.NUM_PIECES; i++) {
            int index = (cycleIndex + i) % GameConstants.NUM_PIECES;
            Piece piece = pieces[index];
            if (isPieceMovable(piece)) {
                cycleIndex = (index + 1) % GameConstants.NUM_PIECES;
                return piece;
            }
        }
        return null;
    }

    private boolean isPieceMovable(Piece piece) {
        return piece.getState() == enums.PieceState.ACTIVE;
    }

    private boolean targetsMystery(Piece piece, Board board) {
        if (isMovingCCW(piece)) {
            return isHeadingTowardsMystery(piece, board);
        }
        return false;
    }

    private boolean isMovingCCW(Piece piece) {
        return piece.getDirection() == Direction.CCW;
    }

    private boolean isHeadingTowardsMystery(Piece piece, Board board) {
        int mysteryPosition = board.getMysteryPosition();
        if (mysteryPosition == GameConstants.NO_POSITION) {
            return false;
        }
        return piece.getPosition() < mysteryPosition;
    }

    private Piece getNextMovablePiece(Board board) {
        for (Piece piece : getPiecesOnBoard()) {
            if (isSafeToMove(piece, board)) {
                return piece;
            }
        }
        return null;
    }

    private boolean isSafeToMove(Piece piece, Board board) {
        int mysteryPosition = board.getMysteryPosition();
        if (mysteryPosition == GameConstants.NO_POSITION) {
            return true;
        }
        return piece.getPosition() != mysteryPosition;
    }
}