package player.strategy;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

public class BlockerStrategy implements PieceSelectionStrategy {

    private final AbstractPlayer player;

    public BlockerStrategy(AbstractPlayer player) {
        this.player = player;
    }

    @Override
    public Piece choosePiece(int roll, Board board) {
        if (roll == GameConstants.MAX_DICE_ROLL && player.hasPiecesAtBase()) {
            return player.getPiecesAtBase().getFirst();
        }

        if (canFormBlock(roll, board)) {
            return getPieceToFormBlock(roll, board);
        }

        if (hasBlockOnBoard(board)) {
            return getBlockPiece(board);
        }

        Piece capturePiece = findCaptureForHome(roll, board);
        if (capturePiece != null) {
            return capturePiece;
        }

        return avoidCapture(board);
    }

    private boolean canFormBlock(int roll, Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return true;
            }
        }
        return false;
    }

    private boolean pieceCanFormBlock(Piece piece, int roll, Board board) {
        int targetCell = piece.getPosition() + roll;
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
        for (Piece piece : player.getPiecesOnBoard()) {
            if (pieceCanFormBlock(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }

    private boolean hasBlockOnBoard(Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                return true;
            }
        }
        return false;
    }

    private Piece getBlockPiece(Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (board.getPiecesAt(piece.getPosition()).size() >= GameConstants.MIN_BLOCK_SIZE) {
                return piece;
            }
        }
        return null;
    }

    private Piece findCaptureForHome(int roll, Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
            if (piece.getCaptureCount() >= GameConstants.MIN_CAPTURES_FOR_HOME) {
                continue;
            }
            if (player.canCaptureOpponent(piece, roll, board)) {
                return piece;
            }
        }
        return null;
    }

    private Piece avoidCapture(Board board) {
        Piece leastAdvanced = null;
        int maxDistanceToHome = 0;
        for (Piece piece : player.getPiecesOnBoard()) {
            int distance = board.distanceToHome(piece);
            if (distance > maxDistanceToHome) {
                maxDistanceToHome = distance;
                leastAdvanced = piece;
            }
        }
        return leastAdvanced;
    }
}