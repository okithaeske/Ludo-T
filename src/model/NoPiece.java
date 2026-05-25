package model;

import enums.Colour;

public class NoPiece extends Piece {

    private static final NoPiece INSTANCE = new NoPiece();

    private NoPiece() {
        super("NONE", Colour.RED);
    }

    public static NoPiece getInstance() {
        return INSTANCE;
    }

    public boolean isNull() {
        return true;
    }
}