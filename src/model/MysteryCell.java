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

    public void spawn(Board board) {
        int newPosition;
        do {
            newPosition = RandomInitiator.getInstance().nextInt(GameConstants.BOARD_SIZE);
        } while (newPosition == lastPosition);

        position = newPosition;
        lastPosition = newPosition;
        roundsRemaining = 4;
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

    public TeleportDest getDestination() {
        TeleportDest[] destinations = TeleportDest.values();
        return destinations[RandomInitiator.getInstance().nextInt(destinations.length)];
    }

    public void relocate(Board board) {
        int newPosition;
        do {
            newPosition = RandomInitiator.getInstance().nextInt(52);
        } while (newPosition == lastPosition);

        lastPosition = position;
        position = newPosition;
        roundsRemaining = 4;
    }


}
