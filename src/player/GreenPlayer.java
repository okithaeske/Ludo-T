package player;

import enums.Colour;
import player.strategy.BlockerStrategy;

import java.util.List;

public class GreenPlayer extends AbstractPlayer {
    public GreenPlayer() {
        super(Colour.GREEN, "Green");
        setStrategy(new BlockerStrategy(this));
    }
}