package test.test;

import engine.GameEngine;
import engine.GameEngineBuilder;
import enums.GameMode;
import logger.GameEventListener;
import model.GameConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameEngine")
class GameEngineTest extends BaseTest {

    private static final int MAX_ROUNDS = 50_000;

    // All GameEventListener methods are default no-ops, so this anonymous class
    // silences console output in tests without any override boilerplate.
    private static final GameEventListener SILENT = new GameEventListener() { };

    @Test
    @DisplayName("should_completeGame_when_ludoTModeRunsWithFixedSeed")
    void should_completeGame_when_ludoTModeRunsWithFixedSeed() {
        // Arrange
        GameEngine game = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(42L)
                .withListener(SILENT)
                .build();

        // Act
        runUntilOver(game);

        // Assert
        assertTrue(game.isGameOver(), "Game did not finish within " + MAX_ROUNDS + " rounds");
    }

    @Test
    @DisplayName("should_notThrow_when_classicModeRunsSeveralRounds")
    void should_notThrow_when_classicModeRunsSeveralRounds() {
        // this test verifies at least 200 rounds execute without errors.
        GameEngine game = new GameEngineBuilder()
                .withMode(GameMode.CLASSIC)
                .withSeed(1L)
                .withListener(SILENT)
                .build();

        // Act / Assert — no exception thrown
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 200 && !game.isGameOver(); i++) {
                game.executeRound();
            }
        });
    }

    @Test
    @DisplayName("should_notBeGameOver_when_noRoundsHaveBeenPlayed")
    void should_notBeGameOver_when_noRoundsHaveBeenPlayed() {
        // Arrange
        GameEngine game = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(1L)
                .withListener(SILENT)
                .build();

        // Assert
        assertFalse(game.isGameOver());
    }

    @Test
    @DisplayName("should_produceConsistentResult_when_sameSeedUsedTwice")
    void should_produceConsistentResult_when_sameSeedUsedTwice() {
        // Arrange — game 1
        GameEngine game1 = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(7L)
                .withListener(SILENT)
                .build();
        int rounds1 = runUntilOver(game1);

        // Arrange — game 2 with same seed
        GameEngine game2 = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(7L)
                .withListener(SILENT)
                .build();
        int rounds2 = runUntilOver(game2);

        // Assert — deterministic: same seed must produce same number of rounds
        assertEquals(rounds1, rounds2);
    }

    @Test
    @DisplayName("should_buildSuccessfully_when_noListenerSpecified")
    void should_buildSuccessfully_when_noListenerSpecified() {
        // Builder must supply a default Logger when no listener is added.
        assertDoesNotThrow(() -> new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(42L)
                .build());
    }

    @Test
    @DisplayName("should_spawnMysteryCell_when_twoRoundsHavePassedWithPiecesOnBoard")
    void should_spawnMysteryCell_when_twoRoundsHavePassedWithPiecesOnBoard() {
        // Arrange — capture mystery-spawn event via listener.
        // MysteryCellManager spawns after MYSTERY_SPAWN_ROUND (2) rounds with ≥1 piece on the path.
        boolean[] spawned = {false};
        GameEventListener listener = new GameEventListener() {
            @Override
            public void onMysterySpawn(int position) {
                spawned[0] = true;
            }
        };

        GameEngine game = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .withSeed(42L)
                .withListener(listener)
                .build();

        // Act — run up to 20 rounds; mystery must have spawned well within that window.
        for (int i = 0; i < 20 && !game.isGameOver() && !spawned[0]; i++) {
            game.executeRound();
        }

        // Assert
        assertTrue(spawned[0], "Mystery cell should spawn after 2 rounds with pieces on the board");
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
