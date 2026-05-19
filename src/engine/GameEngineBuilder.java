package engine;

import enums.GameMode;
import model.GameConstants;
import model.RandomInitiator;

public class GameEngineBuilder {

    private GameMode gameMode = GameMode.CLASSIC;
    private int maxRounds = GameConstants.MAX_ROUNDS;
    private long seed = -1;

    public GameEngineBuilder withMode(GameMode gameMode) {
        this.gameMode = gameMode;
        return this;
    }

    public GameEngineBuilder withMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
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
        return new GameEngine(gameMode, maxRounds);
    }
}