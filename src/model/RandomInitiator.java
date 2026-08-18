package model;

import java.util.Random;

/**
 * Default {@link RandomSource} implementation, backed by {@link Random}.
 *
 * <p>Instances are independent: {@code new RandomInitiator(seed)} gives one game its own
 * number stream. The {@link #getInstance() singleton} is retained only as the default source
 * for callers that do not inject one (the CLI path and the existing test suite) — it must not
 * be used by concurrently running games, because {@link #setSeed(long)} on it is a
 * process-wide side effect.
 */
public class RandomInitiator implements RandomSource {

    // Initialization-on-demand holder: thread-safe without synchronisation overhead.
    private static final class Holder {
        static final RandomInitiator INSTANCE = new RandomInitiator();
    }

    private Random random;

    /** Creates an independently seeded source. */
    public RandomInitiator() {
        random = new Random();
    }

    /** Creates a source with a fixed seed, giving a reproducible stream. */
    public RandomInitiator(long seed) {
        random = new Random(seed);
    }

    /** Returns the shared default {@code RandomInitiator} instance. Thread-safe. */
    public static RandomInitiator getInstance() {
        return Holder.INSTANCE;
    }

    public void setSeed(long seed) {
        random = new Random(seed);
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
