package model;

import java.util.Random;

public class Dice {

    private static Dice instance;

    private Dice() { }

    // Singleton pattern is used to ensure only one instance of Dice exists
    public static Dice getInstance() {
        if (instance == null) {
            instance = new Dice();
        }
        return instance;
    }

    public int roll() {
        return RandomInitiator.getInstance().nextInt(6) + 1;
    }

}
