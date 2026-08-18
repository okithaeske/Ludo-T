package engine;

import model.Dice;
import model.GameConstants;
import model.RandomSource;
import player.AbstractPlayer;

import java.util.ArrayList;
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

    /** Uses the shared default dice. */
    public TurnManager(List<AbstractPlayer> players) {
        this(players, Dice.getInstance());
    }

    public TurnManager(List<AbstractPlayer> players, RandomSource randomSource) {
        this(players, new Dice(randomSource));
    }

    private TurnManager(List<AbstractPlayer> players, Dice dice) {
        this.turnOrder = players;
        this.currentPlayerIndex = 0;
        this.consecutiveSixes = 0;
        this.extraRollPending = false;
        this.dice = dice;
        this.consecutiveThreesPerPlayer = new HashMap<>();
    }

    /** The dice this manager rolls — shared with collaborators that must use the same stream. */
    Dice getDice() {
        return dice;
    }

    public int rollDice(AbstractPlayer player) {
        int roll = dice.roll();
        updateConsecutiveSixes(roll);
        updateConsecutiveThrees(roll, player);
        return roll;
    }

    private void updateConsecutiveSixes(int roll) {
        if (roll == GameConstants.MAX_DICE_ROLL) {
            consecutiveSixes++;
        } else {
            consecutiveSixes = 0;
        }
    }

    private void updateConsecutiveThrees(int roll, AbstractPlayer player) {
        if (roll == GameConstants.FROZEN_ESCAPE_ROLL) {
            consecutiveThreesPerPlayer.merge(player, 1, Integer::sum);
        } else {
            consecutiveThreesPerPlayer.put(player, 0);
        }
    }

    /** Moves to the next player and resets consecutive sixes. */
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
        List<AbstractPlayer> reordered = new ArrayList<>();
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

    public List<AbstractPlayer> getTurnOrder() {
        return new ArrayList<>(turnOrder);
    }

    public void resetRollStreaks() {
        consecutiveSixes = 0;
        extraRollPending = false;
        consecutiveThreesPerPlayer.clear();
    }
}
