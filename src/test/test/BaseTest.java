package test.test;

import model.RandomInitiator;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseTest {

    @BeforeEach
    void resetSeed() {
        RandomInitiator.getInstance().setSeed(42L);
    }
}

