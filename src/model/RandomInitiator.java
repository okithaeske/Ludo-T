package model;

import java.util.Random;

public class RandomInitiator {

    // Initialization-on-demand holder: thread-safe without synchronisation overhead.
    private static final class Holder {
        static final RandomInitiator INSTANCE = new RandomInitiator();
    }

    private Random random;

    private RandomInitiator() {
        random = new Random();
    }

    /** Returns the single shared {@code RandomInitiator} instance. Thread-safe. */
    public static RandomInitiator getInstance() {
        return Holder.INSTANCE;
    }

    public void setSeed(long seed) {
        random = new Random(seed);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
