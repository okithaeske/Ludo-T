package engine;

import enums.GameMode;
import logger.GameEventListener;
import logger.Logger;
import model.RandomInitiator;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Builder pattern</b> — constructs a {@link GameEngine} with a fluent API.
 *
 * <p>Also satisfies the <b>Dependency Inversion Principle</b>: callers inject
 * concrete {@link GameEventListener} implementations rather than having
 * {@code GameEngine} hard-wire {@link Logger} internally.
 *
 * <pre>{@code
 * GameEngine game = new GameEngineBuilder()
 *         .withMode(GameMode.LUDO_T)
 *         .withSeed(42)
 *         .withListener(new Logger())
 *         .build();
 * }</pre>
 */
public class GameEngineBuilder {

    private GameMode gameMode = GameMode.CLASSIC;
    private long seed = -1;
    private final List<GameEventListener> listeners = new ArrayList<>();

    public GameEngineBuilder withMode(GameMode gameMode) {
        this.gameMode = gameMode;
        return this;
    }

    public GameEngineBuilder withSeed(long seed) {
        this.seed = seed;
        return this;
    }

    /** Adds a listener to receive game events. May be called multiple times. */
    public GameEngineBuilder withListener(GameEventListener listener) {
        listeners.add(listener);
        return this;
    }

    public GameEngine build() {
        if (seed != -1) {
            RandomInitiator.getInstance().setSeed(seed);
        }
        List<GameEventListener> effective = listeners.isEmpty()
                ? List.of(new Logger())
                : new ArrayList<>(listeners);
        return new GameEngine(gameMode, effective);
    }
}
