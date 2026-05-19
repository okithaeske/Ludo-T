package player;

import enums.Colour;
import player.strategy.MysteryHunterStrategy;

public class BluePlayer extends AbstractPlayer {
    public BluePlayer() {
        super(Colour.BLUE, "Blue");
        setStrategy(new MysteryHunterStrategy(this));
    }
}