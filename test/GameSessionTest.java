package test;

import adapter.GameEventBroadcaster;
import app.GameSession;
import app.SessionRegistry;
import app.model.GameSnapshot;
import app.model.PieceView;
import app.model.PlayerView;
import app.model.SessionState;
import app.port.Clock;
import app.port.EventSink;
import app.port.GameRepository;
import app.port.SessionEvent;
import app.port.SessionEventType;
import enums.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@DisplayName("GameSession lifecycle")
class GameSessionTest {

    private final List<SessionEvent> published = new CopyOnWriteArrayList<>();
    private final EventSink recordingSink = published::add;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry(recordingSink, GameRepository.NO_OP, Clock.SYSTEM,
                GameEventBroadcaster::new);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private GameSession newSession(long tickMillis) {
        return registry.create(GameMode.LUDO_T, 42L, tickMillis);
    }

    private GameSnapshot snapshotOf(GameSession session) throws Exception {
        return session.snapshot().get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("should_beCreatedAtRoundZero_when_sessionIsNew")
    void should_beCreatedAtRoundZero_when_sessionIsNew() throws Exception {
        // Arrange / Act
        GameSession session = newSession(0L);
        GameSnapshot snapshot = snapshotOf(session);

        // Assert — creating a game must not start it
        Assertions.assertEquals(SessionState.CREATED, session.getState());
        Assertions.assertEquals(0, snapshot.round());
        Assertions.assertEquals(4, snapshot.players().size());
    }

    @Test
    @DisplayName("should_advanceExactlyOneRound_when_stepped")
    void should_advanceExactlyOneRound_when_stepped() throws Exception {
        // Arrange
        GameSession session = newSession(0L);

        // Act
        session.step().get(10, TimeUnit.SECONDS);

        // Assert
        Assertions.assertEquals(1, snapshotOf(session).round());

        session.step().get(10, TimeUnit.SECONDS);
        Assertions.assertEquals(2, snapshotOf(session).round());
    }

    @Test
    @DisplayName("should_stopAdvancing_when_paused")
    void should_stopAdvancing_when_paused() throws Exception {
        // Arrange — a slow tick so the pause lands mid-game rather than after it finishes
        GameSession session = newSession(20L);
        session.start().get(10, TimeUnit.SECONDS);
        Thread.sleep(150);

        // Act
        session.pause().get(10, TimeUnit.SECONDS);
        int roundAtPause = snapshotOf(session).round();
        Thread.sleep(200);

        // Assert
        Assertions.assertEquals(SessionState.PAUSED, session.getState());
        Assertions.assertEquals(roundAtPause, snapshotOf(session).round(),
                "A paused game must not advance");
    }

    @Test
    @DisplayName("should_resumeFromWhereItPaused_when_resumed")
    void should_resumeFromWhereItPaused_when_resumed() throws Exception {
        // Arrange
        GameSession session = newSession(10L);
        session.start().get(10, TimeUnit.SECONDS);
        Thread.sleep(100);
        session.pause().get(10, TimeUnit.SECONDS);
        int roundAtPause = snapshotOf(session).round();

        // Act
        session.resume().get(10, TimeUnit.SECONDS);
        Thread.sleep(150);

        // Assert
        Assertions.assertTrue(snapshotOf(session).round() > roundAtPause,
                "A resumed game must continue advancing");
    }

    @Test
    @DisplayName("should_reachFinishedAndPublishResult_when_gameRunsToCompletion")
    void should_reachFinishedAndPublishResult_when_gameRunsToCompletion() throws Exception {
        // Arrange — tick as fast as possible
        GameSession session = newSession(0L);

        // Act
        session.start().get(10, TimeUnit.SECONDS);
        waitUntil(() -> session.getState() == SessionState.FINISHED, 120_000);

        // Assert
        Assertions.assertEquals(SessionState.FINISHED, session.getState());
        GameSnapshot finalSnapshot = snapshotOf(session);
        Assertions.assertTrue(finalSnapshot.gameOver());
        Assertions.assertEquals(4, finalSnapshot.finishingOrder().size(),
                "Every player should be placed once the game ends");
        Assertions.assertTrue(
                published.stream().anyMatch(e -> e.type() == SessionEventType.FINISHED),
                "A finished game must publish a FINISHED event");
    }

    @Test
    @DisplayName("should_refuseToStep_when_gameHasBeenAborted")
    void should_refuseToStep_when_gameHasBeenAborted() throws Exception {
        // Arrange
        GameSession session = newSession(0L);
        session.step().get(10, TimeUnit.SECONDS);
        int roundBefore = snapshotOf(session).round();

        // Act
        session.abort().get(10, TimeUnit.SECONDS);

        // Assert — the session is terminal, so further work is rejected outright
        Assertions.assertEquals(SessionState.ABORTED, session.getState());
        Assertions.assertThrows(Exception.class,
                () -> session.step().get(5, TimeUnit.SECONDS),
                "An aborted session must not accept further commands");
        Assertions.assertEquals(roundBefore, session.summary().round());
    }

    @Test
    @DisplayName("should_keepPieceCountsConsistent_when_snapshotTakenDuringPlay")
    void should_keepPieceCountsConsistent_when_snapshotTakenDuringPlay() throws Exception {
        // Guards the reason snapshots are taken on the session thread: a snapshot captured
        // while a round was running would show a torn board, most visibly as a player whose
        // pieces do not add up to four.
        GameSession session = newSession(0L);
        session.start().get(10, TimeUnit.SECONDS);

        for (int attempt = 0; attempt < 50; attempt++) {
            GameSnapshot snapshot = snapshotOf(session);
            for (PlayerView player : snapshot.players()) {
                Assertions.assertEquals(4, player.pieces().size());

                long accountedFor = player.pieces().stream()
                        .map(PieceView::state)
                        .filter(state -> state.equals("BASE") || state.equals("ACTIVE")
                                || state.equals("HOME"))
                        .count();
                Assertions.assertEquals(4, accountedFor,
                        "Torn snapshot: a piece was in no recognised state");
            }
            if (snapshot.gameOver()) {
                break;
            }
        }
    }

    @Test
    @DisplayName("should_runIndependently_when_severalSessionsPlayAtOnce")
    void should_runIndependently_when_severalSessionsPlayAtOnce() throws Exception {
        // Arrange — same seed, so identical play unless they interfere with each other
        List<GameSession> sessions = List.of(
                registry.create(GameMode.LUDO_T, 7L, 0L),
                registry.create(GameMode.LUDO_T, 7L, 0L),
                registry.create(GameMode.LUDO_T, 7L, 0L),
                registry.create(GameMode.LUDO_T, 7L, 0L));

        // Act — all four in flight together
        for (GameSession session : sessions) {
            session.start().get(10, TimeUnit.SECONDS);
        }
        for (GameSession session : sessions) {
            waitUntil(() -> session.getState() == SessionState.FINISHED, 120_000);
        }

        // Assert — identical results prove no shared state leaked between them
        int expectedRounds = sessions.get(0).summary().round();
        for (GameSession session : sessions) {
            Assertions.assertEquals(expectedRounds, session.summary().round(),
                    "Concurrent same-seed games diverged — state is being shared between sessions");
        }
        Assertions.assertEquals(4, registry.liveCount());
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        Assertions.fail("Condition not met within " + timeoutMillis + "ms");
    }
}
