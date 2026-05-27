package model;

import enums.TeleportDest;

public class MysteryCell {

    private int position;
    private int roundsRemaining;
    private int lastPosition;
    private boolean active;
    private int roundsSinceActive;

    public MysteryCell(int position) {
        this.position = position;
        this.roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        this.lastPosition = GameConstants.BASE_POSITION;
        this.active = false;
        this.roundsSinceActive = 0;
    }

    // Rule T-10: only spawn when at least one piece is on the standard path
    public void spawn(Board board) {
        // Only spawn if there is at least one piece on the standard path (cells 0–51)
        boolean piecesOnPath = false;
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            if (board.isOccupied(i)) {
                piecesOnPath = true;
                break;
            }
        }
        if (!piecesOnPath) {
            return;
        }

        int newPosition;
        do {
            newPosition = RandomInitiator.getInstance().nextInt(GameConstants.BOARD_SIZE);
        } while (newPosition == lastPosition || board.isOccupied(newPosition));

        position = newPosition;
        lastPosition = newPosition;
        roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        active = true;
        roundsSinceActive = 0;
    }

    public void tick() {
        if (active) {
            roundsRemaining--;
            if (roundsRemaining <= 0) {
                relocate(null);
            }
        } else {
            roundsSinceActive++;
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getPosition() {
        return position;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }

    public TeleportDest getDestination() {
        TeleportDest[] destinations = TeleportDest.values();
        return destinations[RandomInitiator.getInstance().nextInt(destinations.length)];
    }

    // Rule T-10: relocate must set active=true and avoid occupied cells
    public void relocate(Board board) {
        int newPosition;
        if (board != null) {
            do {
                newPosition = RandomInitiator.getInstance().nextInt(GameConstants.BOARD_SIZE);
            } while (newPosition == lastPosition || board.isOccupied(newPosition));
        } else {
            do {
                newPosition = RandomInitiator.getInstance().nextInt(GameConstants.BOARD_SIZE);
            } while (newPosition == lastPosition);
        }

        lastPosition = position;
        position = newPosition;
        roundsRemaining = GameConstants.MYSTERY_CELL_DURATION;
        active = true;  // Bug 2.1 fix: was missing
    }
}
