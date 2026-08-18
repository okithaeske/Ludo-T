package test;

import engine.GameEngine;
import engine.GameEngineBuilder;
import enums.GameMode;
import logger.GameEventListener;
import model.RandomInitiator;
import model.RandomSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Guards the per-game randomness invariant that the server tier depends on: a seeded game
 * must produce the same result whether it runs alone or alongside other games.
 *
 * <p>Before {@link RandomSource} was introduced, every game drew from one process-wide
 * {@code Random} inside the {@code RandomInitiator} singleton, so concurrent games consumed
 * each other's number stream and building a seeded game reseeded games already in flight.
 */
@DisplayName("Per-game RandomSource isolation")
class RandomSourceIsolationTest extends BaseTest {

    private static final int MAX_ROUNDS = 50_000;
    private static final long SEED = 7L;
    private static final int CONCURRENT_GAMES = 8;

    private static final GameEventListener SILENT = new GameEventListener() { };

    @Test
    @DisplayName("should_produceSameResultAsSoloRun_when_manySeededGamesRunConcurrently")
    void should_produceSameResultAsSoloRun_when_manySeededGamesRunConcurrently() throws Exception {
        // Arrange — the reference result, measured with nothing else running.
        int expectedRounds = runUntilOver(buildSeededGame(SEED));

        List<Callable<Integer>> games = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_GAMES; i++) {
            games.add(() -> runUntilOver(buildSeededGame(SEED)));
        }

        // Act — same seed, all in flight at once.
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_GAMES);
        List<Future<Integer>> results;
        try {
            results = pool.invokeAll(games);
        } finally {
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(2, TimeUnit.MINUTES), "Games did not finish");
        }

        // Assert — every concurrent game matches the solo run, so no stream was shared.
        for (Future<Integer> result : results) {
            Assertions.assertEquals(expectedRounds, result.get(),
                    "A concurrently running seeded game diverged from its solo result");
        }
    }

    @Test
    @DisplayName("should_notDisturbRunningGame_when_anotherSeededGameIsBuilt")
    void should_notDisturbRunningGame_when_anotherSeededGameIsBuilt() {
        // Arrange — the reference result for a game played straight through.
        int expectedRounds = runUntilOver(buildSeededGame(SEED));

        // Act — play the same game, building differently seeded games partway through.
        GameEngine game = buildSeededGame(SEED);
        int rounds = 0;
        while (!game.isGameOver() && rounds < MAX_ROUNDS) {
            game.executeRound();
            rounds++;
            if (rounds % 5 == 0) {
                buildSeededGame(rounds); // would have reseeded the shared singleton
            }
        }

        // Assert
        Assertions.assertEquals(expectedRounds, rounds,
                "Building another seeded game changed the outcome of a game already in progress");
    }

    @Test
    @DisplayName("should_drawIndependentStreams_when_twoSourcesShareASeed")
    void should_drawIndependentStreams_when_twoSourcesShareASeed() {
        // Arrange
        RandomSource first = new RandomInitiator(SEED);
        RandomSource second = new RandomInitiator(SEED);

        // Act / Assert — interleaved draws must agree; a shared stream would alternate.
        for (int i = 0; i < 100; i++) {
            Assertions.assertEquals(first.nextInt(6), second.nextInt(6));
        }
    }

    private GameEngine buildSeededGame(long seed) {
        return new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(seed)
                .withListener(SILENT)
                .build();
    }

    private int runUntilOver(GameEngine game) {
        int rounds = 0;
        while (!game.isGameOver() && rounds < MAX_ROUNDS) {
            game.executeRound();
            rounds++;
        }
        return rounds;
    }
}
