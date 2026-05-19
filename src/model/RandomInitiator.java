package model;

import java.util.Random;

public class RandomInitiator {

    private static RandomInitiator instance;
    private Random random;

    private RandomInitiator() {
        random = new Random();
    }

    public static RandomInitiator getInstance() {
        if (instance == null) {
            instance = new RandomInitiator();
        }
        return instance;
    }

    public void setSeed(long seed) {
        random = new Random(seed);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }


}
