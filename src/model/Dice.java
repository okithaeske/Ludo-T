package model;

public class Dice {

    // Initialization-on-demand holder: thread-safe without synchronisation overhead.
    private static final class Holder {
        static final Dice INSTANCE = new Dice();
    }

    private Dice() { }

    /** Returns the single shared {@code Dice} instance. Thread-safe. */
    public static Dice getInstance() {
        return Holder.INSTANCE;
    }

    public int roll() {
        return RandomInitiator.getInstance().nextInt(GameConstants.MAX_DICE_ROLL)
                + GameConstants.MIN_DICE_ROLL;
    }
}
