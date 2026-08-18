package model;

import enums.TeleportDest;

public class MysteryCell {

    private int position;
    private int roundsRemaining;
    private int lastPosition;
    private boolean active;
    private final RandomSource randomSource;

    /** Uses the shared default random source. */
    public MysteryCell(int position) {
        this(position, RandomInitiator.getInstance());
    }

    public MysteryCell(int position, RandomSource randomSource) {
        this.position = position;
        this.roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        this.lastPosition = GameConstants.NO_POSITION;
        this.active = false;
        this.randomSource = randomSource;
    }

    /** Spawns only on an empty standard-path cell. Returns true only when a cell was found. */
    public boolean spawn(Board board) {
        if (!hasAnyPieceOnStandardPath(board)) {
            return false;
        }
        int newPosition = findEmptyMysteryPosition(board);
        if (newPosition == GameConstants.NO_POSITION) {
            return false;
        }
        position = newPosition;
        lastPosition = newPosition;
        roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        active = true;
        return true;
    }

    /** Ticks one completed round. Returns true when the mystery cell relocated. */
    public boolean tick(Board board) {
        if (!active) {
            return false;
        }
        roundsRemaining--;
        if (roundsRemaining <= 0) {
            relocate(board);
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return active;
    }

    public int getPosition() {
        return active ? position : GameConstants.NO_POSITION;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }

    public TeleportDest getDestination() {
        TeleportDest[] destinations = TeleportDest.values();
        return destinations[randomSource.nextInt(destinations.length)];
    }

    /** Relocates to an empty standard-path cell and never repeats the previous location. */
    public void relocate(Board board) {
        int newPosition = findEmptyMysteryPosition(board);
        if (newPosition == GameConstants.NO_POSITION) {
            active = false;
            position = GameConstants.NO_POSITION;
            return;
        }
        lastPosition = position;
        position = newPosition;
        roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        active = true;
    }

    private int findEmptyMysteryPosition(Board board) {
        // Try random candidates first (MYSTERY_FIND_EMPTY_RETRIES = BOARD_SIZE * 3 gives a
        // high probability of finding an empty cell quickly on a typical board).
        for (int attempt = 0; attempt < GameConstants.MYSTERY_FIND_EMPTY_RETRIES; attempt++) {
            int candidate = randomSource.nextInt(GameConstants.BOARD_SIZE);
            if (candidate != lastPosition && !board.isOccupied(candidate)) {
                return candidate;
            }
        }
        // Deterministic fallback: scan all cells to guarantee we find one if it exists.
        for (int candidate = 0; candidate < GameConstants.BOARD_SIZE; candidate++) {
            if (candidate != lastPosition && !board.isOccupied(candidate)) {
                return candidate;
            }
        }
        return GameConstants.NO_POSITION;
    }

    private boolean hasAnyPieceOnStandardPath(Board board) {
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            if (board.isOccupied(i)) {
                return true;
            }
        }
        return false;
    }
}
