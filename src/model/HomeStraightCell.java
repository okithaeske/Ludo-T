package model;

import enums.Colour;

public class HomeStraightCell {

    private final int id;
    private final Colour colour;

    public HomeStraightCell(int id, Colour colour) {
        this.id = id;
        this.colour = colour;
    }

    public int getId() {
        return id;
    }

    public Colour getColour() {
        return colour;
    }

}
