package model;

public class Dice {

    // Initialization-on-demand holder: thread-safe without synchronisation overhead.
    private static final class Holder {
        static final Dice INSTANCE = new Dice(RandomInitiator.getInstance());
    }

    private final RandomSource randomSource;

    /** Creates a dice drawing from the given source — one per game. */
    public Dice(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    /** Returns the shared {@code Dice} backed by the default random source. Thread-safe. */
    public static Dice getInstance() {
        return Holder.INSTANCE;
    }

    public int roll() {
        return randomSource.nextInt(GameConstants.MAX_DICE_ROLL)
                + GameConstants.MIN_DICE_ROLL;
    }
}
