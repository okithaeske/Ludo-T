package model;

import enums.Colour;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Board {

    private final StandardCell[] cells;
    private final HomeStraightCell[][] homeStraights;
    private final Map<Colour, Integer> approachCells;
    private final Map<Colour, Integer> startCells;
    private MysteryCell mysteryCell;

    public Board() {
        cells = new StandardCell[GameConstants.BOARD_SIZE];
        homeStraights = new HomeStraightCell[GameConstants.NUM_PLAYERS][GameConstants.HOME_STRAIGHT_SIZE];
        approachCells = new HashMap<>();
        startCells  = new HashMap<>();
        initialiseCells();
        initialiseApproachCells();
        initialiseStartCells();
    }

    private void initialiseCells() {
        initialiseStandardCells();
        initialiseHomeStraightCells();
    }

    private void initialiseStandardCells() {
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            cells[i] = new StandardCell(i);
        }
    }

    private void initialiseHomeStraightCells() {
        for (int colour = 0; colour < GameConstants.NUM_PLAYERS; colour++) {
            for (int cell = 0; cell < GameConstants.HOME_STRAIGHT_SIZE; cell++) {
                homeStraights[colour][cell] = new HomeStraightCell(cell, Colour.values()[colour]);
            }
        }
    }

    private void initialiseApproachCells() {
        approachCells.put(Colour.RED, GameConstants.RED_APPROACH);
        approachCells.put(Colour.GREEN, GameConstants.GREEN_APPROACH);
        approachCells.put(Colour.YELLOW, GameConstants.YELLOW_APPROACH);
        approachCells.put(Colour.BLUE, GameConstants.BLUE_APPROACH);
    }

    private void initialiseStartCells() {
        startCells.put(Colour.RED, GameConstants.RED_START);
        startCells.put(Colour.GREEN, GameConstants.GREEN_START);
        startCells.put(Colour.YELLOW, GameConstants.YELLOW_START);
        startCells.put(Colour.BLUE, GameConstants.BLUE_START);
    }

    public StandardCell getCell(int id) {
        return cells[id];
    }

    public int getApproach(Colour colour) {
        return approachCells.get(colour);
    }

    public int getStartX(Colour colour) {
        return startCells.get(colour);
    }

    public int distanceToHome(Piece piece) {
        return getApproach(piece.getColour()) - piece.getPosition();
    }

    public List<Piece> getPiecesAt(int id) {
        // TODO: implement when players are initialised in GameEngine
        List<Piece> piecesAtCell = new ArrayList<>();
        return piecesAtCell;
    }

    public boolean isOccupied(int id) {
        return hasPiecesAt(id);
    }

    private boolean hasPiecesAt(int id) {
        return !getPiecesAt(id).isEmpty();
    }

    public int getMysteryPosition() {
        if (mysteryCell == null) {
            return -1;
        }
        return mysteryCell.getPosition();
    }

    public MysteryCell getMysteryCell() {
        return mysteryCell;
    }

    public void setMysteryCell(MysteryCell mysteryCell) {
        this.mysteryCell = mysteryCell;
    }


}
