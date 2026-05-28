package engine;

import model.Dice;
import model.GameConstants;
import player.AbstractPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TurnManager {

    private int currentPlayerIndex;
    private List<AbstractPlayer> turnOrder;
    private int consecutiveSixes;
    private boolean extraRollPending;
    private final Dice dice;
    private final Map<AbstractPlayer, Integer> consecutiveThreesPerPlayer;

    public TurnManager(List<AbstractPlayer> players) {
        this.turnOrder = players;
        this.currentPlayerIndex = 0;
        this.consecutiveSixes = 0;
        this.extraRollPending = false;
        this.dice = Dice.getInstance();
        this.consecutiveThreesPerPlayer = new HashMap<>();
    }

    public int rollDice() {
        int roll = dice.roll();
        updateConsecutiveRolls(roll, getCurrentPlayer());
        return roll;
    }

    private void updateConsecutiveRolls(int roll, AbstractPlayer player) {
        if (roll == GameConstants.MAX_DICE_ROLL) {
            consecutiveSixes++;
        } else {
            consecutiveSixes = 0;
        }

        if (roll == GameConstants.FROZEN_ESCAPE_ROLL) {
            consecutiveThreesPerPlayer.merge(player, 1, Integer::sum);
        } else {
            consecutiveThreesPerPlayer.put(player, 0);
        }
    }

    // Used for extra-roll continuations within the same player's turn (no sixes reset)
    public void nextPlayer() {
        extraRollPending = false;
        currentPlayerIndex = (currentPlayerIndex + 1) % turnOrder.size();
    }

    // Used when genuinely moving to the next player — resets consecutive sixes
    public void advanceToNextPlayer() {
        extraRollPending = false;
        consecutiveSixes = 0;
        currentPlayerIndex = (currentPlayerIndex + 1) % turnOrder.size();
    }

    public void grantExtraRoll() {
        extraRollPending = true;
    }

    public void clearExtraRoll() {
        extraRollPending = false;
    }

    public void handleTripleSix() {
        if (isTripleSix()) {
            extraRollPending = false;
            consecutiveSixes = 0;
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

    public boolean isTripleThreeForPlayer(AbstractPlayer player) {
        return consecutiveThreesPerPlayer.getOrDefault(player, 0) >= GameConstants.TRIPLE_THREE;
    }

    public void resetConsecutiveThreesForPlayer(AbstractPlayer player) {
        consecutiveThreesPerPlayer.put(player, 0);
    }
}
