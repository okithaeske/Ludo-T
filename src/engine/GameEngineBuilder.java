package engine;

import enums.GameMode;
import logger.GameEventListener;
import logger.Logger;
import model.RandomInitiator;
import model.RandomSource;

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
    private RandomSource randomSource;

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

    /**
     * Supplies the randomness for this game explicitly, overriding {@link #withSeed(long)}.
     * Useful when a caller (such as a server session) owns the source's lifecycle.
     */
    public GameEngineBuilder withRandomSource(RandomSource randomSource) {
        this.randomSource = randomSource;
        return this;
    }

    public GameEngine build() {
        List<GameEventListener> effective = listeners.isEmpty()
                ? List.of(new Logger())
                : new ArrayList<>(listeners);
        return new GameEngine(gameMode, effective, resolveRandomSource());
    }

    /**
     * Every built engine gets its <em>own</em> source. Seeding no longer reaches the shared
     * singleton, so creating a seeded game cannot disturb games already running elsewhere in
     * the process, and concurrent games never share a number stream.
     */
    private RandomSource resolveRandomSource() {
        if (randomSource != null) {
            return randomSource;
        }
        return seed == -1 ? new RandomInitiator() : new RandomInitiator(seed);
    }
}
