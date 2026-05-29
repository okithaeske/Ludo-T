package model;

import enums.Colour;
import enums.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Board {

    private final StandardCell[] cells;
    private final Map<Colour, Integer> approachCells;
    private final Map<Colour, Integer> startCells;
    private final Map<Integer, List<Piece>> piecePositions;
    private MysteryCell mysteryCell;

    public Board() {
        cells = new StandardCell[GameConstants.BOARD_SIZE];
        approachCells = new HashMap<>();
        startCells = new HashMap<>();
        piecePositions = new HashMap<>();
        initialiseCells();
        initialiseApproachCells();
        initialiseStartCells();
    }

    private void initialiseCells() {
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            cells[i] = new StandardCell(i);
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

    public void placePiece(Piece piece, int cellId) {
        piecePositions.computeIfAbsent(cellId, k -> new ArrayList<>()).add(piece);
    }

    public void removePiece(Piece piece, int cellId) {
        List<Piece> pieces = piecePositions.get(cellId);
        if (pieces != null) {
            pieces.remove(piece);
        }
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
        if (piece.getState() == enums.PieceState.HOME) {
            return 0;
        }
        if (piece.isInHomeStraight()) {
            return GameConstants.HOME_EXIT_DISTANCE - piece.getHomeStraightPosition();
        }
        if (piece.getPosition() == GameConstants.NO_POSITION) {
            return Integer.MAX_VALUE;
        }

        int approach = getApproach(piece.getColour());
        int position = piece.getPosition();
        if (piece.getDirection() == Direction.CW) {
            return (approach - position + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        return (position - approach + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
    }

    public String getHomeStraightCellName(Piece piece) {
        return piece.getColour().name().toLowerCase()
                + "homepath"
                + (piece.getHomeStraightPosition() - 1);
    }

    public List<Piece> getPiecesAt(int id) {
        return piecePositions.getOrDefault(id, new ArrayList<>());
    }

    public boolean isOccupied(int id) {
        return hasPiecesAt(id);
    }

    private boolean hasPiecesAt(int id) {
        return getPiecesAt(id).size() > 0;
    }

    public int getMysteryPosition() {
        if (mysteryCell == null) {
            return GameConstants.NO_POSITION;
        }
        return mysteryCell.getPosition();
    }

    public MysteryCell getMysteryCell() {
        return mysteryCell;
    }

    public void setMysteryCell(MysteryCell mysteryCell) {
        this.mysteryCell = mysteryCell;
    }

    /**
     * Returns the cell immediately adjacent to {@code cell} in the given direction of travel.
     * Used by {@link engine.TurnExecutor} to compute the "blocked-at" logging cell when a
     * piece or block is stopped one cell before an opponent blockade.
     *
     * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
     */
    public int getAdjacentCell(int cell, Direction direction) {
        // Cell immediately before the blocking cell from the mover's direction of travel.
        if (direction == Direction.CCW) {
            return (cell + 1) % GameConstants.BOARD_SIZE;
        }
        return (cell - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
    }

    /**
     * Projects {@code piece}'s current position forward by {@code steps} cells in its
     * direction of travel, wrapping around the board. Pass
     * {@link Piece#getEffectiveRoll(int) piece.getEffectiveRoll(roll)} for {@code steps} to
     * honour ENERGISED / SICK effects.
     *
     * <p>Replaces the duplicated direction-aware target-cell arithmetic that previously
     * appeared in {@link player.AbstractPlayer}, {@link player.strategy.AggressiveStrategy},
     * and {@link player.strategy.BlockerStrategy}.
     *
     * @see player.AbstractPlayer#canCaptureOpponent(Piece, int, Board)
     */
    public int projectPosition(Piece piece, int steps) {
        if (piece.isInHomeStraight() || piece.getPosition() == GameConstants.NO_POSITION) {
            return GameConstants.NO_POSITION;
        }
        if (piece.getDirection() == Direction.CCW) {
            return (piece.getPosition() - steps + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        }
        return (piece.getPosition() + steps) % GameConstants.BOARD_SIZE;
    }

    /**
     * Builds a {@link Block} from all same-colour pieces at {@code cell}. Returns
     * {@code null} when fewer than {@link GameConstants#MIN_BLOCK_SIZE} such pieces exist,
     * which signals to {@link engine.TurnExecutor} that no block is present.
     *
     * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
     * @see engine.RuleEngine#validateBlockMove(Block, int)
     */
    public Block getBlockAt(int cell, Colour colour, Direction direction) {
        List<Piece> sameColour = getPiecesAt(cell).stream()
                .filter(p -> p.getColour() == colour)
                .collect(Collectors.toList());
        if (sameColour.size() < GameConstants.MIN_BLOCK_SIZE) return null;
        Block block = new Block(cell, direction);
        sameColour.forEach(block::addPiece);
        return block;
    }
}
