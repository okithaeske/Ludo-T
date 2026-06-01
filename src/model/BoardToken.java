package model;

import enums.Colour;

/**
 * <b>Composite pattern</b> — common interface shared by {@link Piece} (leaf, size 1)
 * and {@link Block} (composite, size &ge; 2). Allows engine code to treat a single
 * piece and a block of pieces uniformly when querying board position and colour.
 *
 * @see Piece
 * @see Block
 * @see engine.TurnExecutor
 */
public interface BoardToken {

    /** Cell index on the standard board, or {@link GameConstants#NO_POSITION} if off-board. */
    int getPosition();

    /** Number of pieces represented: 1 for a lone piece, 2+ for a block. */
    int getSize();

    /** Colour of the piece(s). All pieces in a block share the same colour. */
    Colour getColour();
}
