package model;

import enums.Direction;
import java.util.ArrayList;
import java.util.List;

public class Block {

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

    public int getSize() {
        return pieces.size();
    }

    public Direction getDirectionForMove() {
        return direction;
    }

    public void breakBlock(Piece piece) {
        pieces.remove(piece);
    }

    public boolean canBeCaptured(Block attacker) {
        return attacker.getSize() == this.getSize();
    }

    public List<Piece> getPieces() {
        return pieces;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
