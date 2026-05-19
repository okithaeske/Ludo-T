package player;

import enums.Colour;
import player.strategy.RacerStrategy;

public class YellowPlayer extends AbstractPlayer {
    public YellowPlayer() {
        super(Colour.YELLOW, "Yellow");
        setStrategy(new RacerStrategy(this));
    }
}