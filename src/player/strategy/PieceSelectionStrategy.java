package player.strategy;

import model.Board;
import model.Piece;

public interface PieceSelectionStrategy {
    Piece choosePiece(int roll, Board board);
}
