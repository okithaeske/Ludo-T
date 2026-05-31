package model;

import enums.Colour;
import enums.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@link BoardToken} as a <b>Composite pattern</b> composite node
 * (size &ge; 2). Engine code can treat a {@code Block} and a lone {@link Piece}
 * uniformly through the {@code BoardToken} interface.
 */
public class Block implements BoardToken {

    private List<Piece> pieces;
    private int position;
    private Direction direction;

    public Block(int position, Direction direction) {
        this.pieces = new ArrayList<>();
        this.position = position;
        this.direction = direction;
    }

    public void addPiece(Piece piece) {
        pieces.add(piece);
    }

    // BoardToken (Composite node)
    @Override public int getPosition() { return position; }
    @Override public int getSize()     { return pieces.size(); }
    @Override public Colour getColour() {
        return pieces.isEmpty() ? Colour.NONE : pieces.get(0).getColour();
    }

    public Direction getDirectionForMove() {
        return direction;
    }

    public void breakBlock(Piece piece) {
        piece.setDirection(piece.getOriginalDirection());
        pieces.remove(piece);
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public boolean canBeCaptured(Block attacker) {
        return attacker.getSize() == this.getSize();
    }

    public List<Piece> getPieces() {
        return pieces;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
