package engine;

import model.Dice;
import model.GameConstants;
import player.AbstractPlayer;

import java.util.List;

public class TurnManager {

    private int currentPlayerIndex;
    private List<AbstractPlayer> turnOrder;
    private int consecutiveSixes;
    private boolean extraRollPending;
    private final Dice dice;
    private int consecutiveThrees;

    public TurnManager(List<AbstractPlayer> players) {
        this.turnOrder = players;
        this.currentPlayerIndex = 0;
        this.consecutiveSixes = 0;
        this.extraRollPending = false;
        this.dice = Dice.getInstance();
        this.consecutiveThrees = 0;
    }

    public int rollDice() {
        int roll = dice.roll();
        updateConsecutiveRolls(roll);
        return roll;
    }

    private void updateConsecutiveRolls(int roll) {
        if (roll == GameConstants.MAX_DICE_ROLL) {
            consecutiveSixes++;
        } else {
            consecutiveSixes = 0;
        }

        if (roll == GameConstants.FROZEN_ESCAPE_ROLL) {
            consecutiveThrees++;
        } else {
            consecutiveThrees = 0;
        }
    }

    public void nextPlayer() {
        extraRollPending = false;
        consecutiveSixes = 0;
        currentPlayerIndex = (currentPlayerIndex + 1) % turnOrder.size();
    }

    public void grantExtraRoll() {
        extraRollPending = true;
    }

    public void handleTripleSix() {
        if (isTripleSix()) {
            extraRollPending = false;
            consecutiveSixes = 0;
            nextPlayer();
        }
    }

    public boolean isTripleSix() {
        return consecutiveSixes == GameConstants.TRIPLE_SIX;
    }

    public AbstractPlayer getCurrentPlayer() {
        return turnOrder.get(currentPlayerIndex);
    }

    public void setOrder(AbstractPlayer first) {
        int firstIndex = turnOrder.indexOf(first);
        if (firstIndex == GameConstants.NO_POSITION) {
            return;
        }
        reorderFromIndex(firstIndex);
    }

    private void reorderFromIndex(int firstIndex) {
        List<AbstractPlayer> reordered = new java.util.ArrayList<>();
        for (int i = 0; i < turnOrder.size(); i++) {
            reordered.add(turnOrder.get((firstIndex + i) % turnOrder.size()));
        }
        turnOrder = reordered;
        currentPlayerIndex = 0;
    }

    public boolean isExtraRollPending() {
        return extraRollPending;
    }

    public int getConsecutiveSixes() {
        return consecutiveSixes;
    }

    public boolean isTripleThree() {
        return consecutiveThrees >= GameConstants.TRIPLE_THREE;
    }

    public void resetConsecutiveThrees() {
        consecutiveThrees = 0;
    }
}