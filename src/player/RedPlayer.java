package player;

import enums.Colour;
import player.strategy.AggressiveStrategy;


public class RedPlayer extends AbstractPlayer {
    public RedPlayer() {
        super(Colour.RED, "Red");
        setStrategy(new AggressiveStrategy(this));  // assign strategy
    }
}