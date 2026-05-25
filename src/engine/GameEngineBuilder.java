package engine;

import enums.GameMode;
import model.RandomInitiator;

public class GameEngineBuilder {

    private GameMode gameMode = GameMode.CLASSIC;
    private long seed = -1;

    public GameEngineBuilder withMode(GameMode gameMode) {
        this.gameMode = gameMode;
        return this;
    }

    public GameEngineBuilder withSeed(long seed) {
        this.seed = seed;
        return this;
    }

    public GameEngine build() {
        if (seed != -1) {
            RandomInitiator.getInstance().setSeed(seed);
        }
        return new GameEngine(gameMode);
    }
}