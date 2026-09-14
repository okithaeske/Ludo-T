package test.automation;

import app.port.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import persistence.DatabaseConfig;
import persistence.JdbcGameRepository;
import server.ServerAssembly;
import server.ServerConfig;
import shared.Command;
import shared.FinishedGameDto;
import shared.ProtocolCodec;
import shared.Request;
import shared.Response;
import shared.SessionSummaryDto;
import shared.StrategyRankingDto;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * All three tiers at once: a client on a real socket, the real server assembly, and a real
 * database behind it.
 *
 * <h2>Why this test exists</h2>
 * Every pairing was already covered — {@code ServerPushIntegrationTest} proves client↔server
 * over sockets, {@code PersistenceTierTest} proves server↔database against real H2 — but
 * nothing joined the two. The chain this asserts is the one the Architecture criterion is
 * actually about: <em>a game plays to completion, its result crosses two process boundaries in
 * opposite directions, and a client reads it back.</em> Without this, that whole path could
 * break and the suite would stay green; it was previously verified only by hand.
 *
 * <p>It is deliberately end-to-end in the awkward way: the result is not inspected in the
 * database, it is fetched back with {@code GET_HISTORY} over the same socket that started the
 * game. Reading the tables directly would skip the read path, the mapper and the wire format,
 * which is most of what could go wrong between the tiers.
 *
 * <h2>What it does not do</h2>
 * The database runs in-memory rather than as a separate OS process, so this proves the wiring
 * and the protocol, not that {@code run-db.ps1} starts H2 correctly. That last mile is a
 * scripted check, not a unit of code — see IMPLEMENTATION-NOTES §6c for the measured run
 * against three real processes.
 */
@DisplayName("Three tiers end to end (client, server, database)")
class ThreeTierAutomationTest {

    private static final AtomicInteger DB_NAMES = new AtomicInteger();

    /** The game is driven as fast as the engine will go so the test finishes in seconds. */
    private static final long FAST_TICK_MILLIS = 1L;

    private static final long GAME_TIMEOUT_MILLIS = 60_000L;
    private static final long HISTORY_TIMEOUT_MILLIS = 15_000L;

    private final AtomicLong requestIds = new AtomicLong();

    private String url;
    private JdbcGameRepository repository;
    private ServerAssembly server;
    private Socket socket;
    private ProtocolCodec codec;

    @BeforeEach
    void setUp() throws Exception {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            Assumptions.abort("lib\\h2.jar not present; run run-db.ps1 -Init once to fetch it");
        }

        // ── Tier 3: the database, built from the project's own schema ────────
        url = "jdbc:h2:mem:threetier" + DB_NAMES.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        Path schema = Path.of("db", "schema.sql");
        Assumptions.assumeTrue(Files.exists(schema), "db/schema.sql not found");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(schema));
        }
        repository = JdbcGameRepository.connect(new DatabaseConfig(url, "sa", "", 20_000));

        // ── Tier 2: the real composition root, on an ephemeral port ──────────
        server = new ServerAssembly(new ServerConfig(0, 2, 64, 1_000L), repository, Clock.SYSTEM);
        server.start();

        // ── Tier 1: a client on a real socket ────────────────────────────────
        socket = new Socket("localhost", server.getPort());
        codec = ProtocolCodec.open(socket);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (socket != null) {
            socket.close();
        }
        if (server != null) {
            server.close();
        }
        if (repository != null) {
            repository.close();
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    @DisplayName("should_appearInHistory_when_aGamePlayedOverASocketFinishes")
    void should_appearInHistory_when_aGamePlayedOverASocketFinishes() throws Exception {
        Assertions.assertTrue(historyNow().isEmpty(),
                "a freshly created schema must start with no finished games");

        String gameId = createAndFinishOneGame();

        FinishedGameDto stored = awaitInHistory(gameId);
        Assertions.assertEquals("LUDO_T", stored.mode());
        Assertions.assertTrue(stored.rounds() > 0, "a finished game must have played rounds");
        Assertions.assertTrue(stored.hasWinner(), "a finished game must have a winner");
        Assertions.assertFalse(stored.finishingOrder().isEmpty());
        Assertions.assertEquals(stored.winnerColour(), stored.finishingOrder().get(0),
                "the winner is the head of the finishing order");
        Assertions.assertTrue(stored.finishedAtMillis() > 0L,
                "the database stamped the row with a finish time");
    }

    @Test
    @DisplayName("should_countTheWinnerStrategy_when_theLeaderboardIsFetched")
    void should_countTheWinnerStrategy_when_theLeaderboardIsFetched() throws Exception {
        String gameId = createAndFinishOneGame();
        FinishedGameDto stored = awaitInHistory(gameId);

        List<StrategyRankingDto> board = leaderboardNow();
        Assertions.assertEquals(4, board.size(), "one row per strategy that played");

        StrategyRankingDto winner = board.stream()
                .filter(row -> row.strategy().equals(stored.winnerStrategy()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the winning strategy " + stored.winnerStrategy()
                                + " is missing from the leaderboard"));

        // The aggregation is done by the database view, so this asserts the whole path:
        // engine result → writer thread → SQL view → read use case → mapper → wire → client.
        Assertions.assertEquals(1, winner.wins());
        Assertions.assertEquals(1, winner.gamesPlayed());
        Assertions.assertEquals(1.0, winner.winRate());

        for (StrategyRankingDto row : board) {
            Assertions.assertEquals(1, row.gamesPlayed(),
                    row.strategy() + " played the one game like everyone else");
        }
    }

    @Test
    @DisplayName("should_stillAnswer_when_theDatabaseIsAskedForHistoryBeforeAnyGameRuns")
    void should_stillAnswer_when_theDatabaseIsAskedForHistoryBeforeAnyGameRuns() throws Exception {
        // An empty database is a legitimate answer, not an error: the GUI's History tab must
        // not show a failure dialogue against a server that simply has no results yet.
        Response response = call(Command.GET_HISTORY, Map.of());
        Assertions.assertTrue(response.isOk(), "an empty history is OK, not an error");
        Assertions.assertTrue(((List<?>) response.getPayload()).isEmpty());

        Response board = call(Command.GET_LEADERBOARD, Map.of());
        Assertions.assertTrue(board.isOk());
        Assertions.assertTrue(((List<?>) board.getPayload()).isEmpty());
    }

    // ── Driving the game over the socket ─────────────────────────────────────

    private String createAndFinishOneGame() throws Exception {
        Response created = call(Command.CREATE_GAME, Map.of(
                "mode", "LUDO_T",
                // Seeded so a failure is reproducible rather than a one-off.
                "seed", "20260908",
                "tickMillis", String.valueOf(FAST_TICK_MILLIS)));
        Assertions.assertTrue(created.isOk(), "create failed: " + created.getMessage());

        String gameId = ((SessionSummaryDto) created.getPayload()).gameId();
        Assertions.assertTrue(call(Command.START_GAME, Map.of("gameId", gameId)).isOk());

        awaitState(gameId, "FINISHED");
        return gameId;
    }

    private void awaitState(String gameId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + GAME_TIMEOUT_MILLIS;
        String last = "?";
        while (System.currentTimeMillis() < deadline) {
            for (SessionSummaryDto game : listGames()) {
                if (game.gameId().equals(gameId)) {
                    last = game.state();
                    if (expected.equals(last)) {
                        return;
                    }
                }
            }
            Thread.sleep(25);
        }
        Assertions.fail("game " + gameId + " never reached " + expected + "; last state " + last);
    }

    /**
     * Waits for the row to arrive, rather than asserting immediately.
     *
     * <p>Not flakiness tolerance — it is the contract. Writes are fire-and-forget by design so
     * that a game never waits on the database, which means "the game has finished" and "the row
     * is readable" are genuinely two different moments. A test that demanded them to be the
     * same would be asserting the opposite of what this tier promises.
     */
    private FinishedGameDto awaitInHistory(String gameId) throws Exception {
        long deadline = System.currentTimeMillis() + HISTORY_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            for (FinishedGameDto game : historyNow()) {
                if (game.gameId().equals(gameId)) {
                    return game;
                }
            }
            Thread.sleep(50);
        }
        return Assertions.fail("game " + gameId + " never appeared in history; the writer stored "
                + repository.writtenCount() + " row(s), dropped " + repository.droppedCount()
                + ", failed " + repository.failedCount());
    }

    @SuppressWarnings("unchecked")
    private List<SessionSummaryDto> listGames() throws Exception {
        return (List<SessionSummaryDto>) call(Command.LIST_GAMES, Map.of()).getPayload();
    }

    @SuppressWarnings("unchecked")
    private List<FinishedGameDto> historyNow() throws Exception {
        Response response = call(Command.GET_HISTORY, Map.of());
        Assertions.assertTrue(response.isOk(), "history failed: " + response.getMessage());
        return (List<FinishedGameDto>) response.getPayload();
    }

    @SuppressWarnings("unchecked")
    private List<StrategyRankingDto> leaderboardNow() throws Exception {
        Response response = call(Command.GET_LEADERBOARD, Map.of());
        Assertions.assertTrue(response.isOk(), "leaderboard failed: " + response.getMessage());
        return (List<StrategyRankingDto>) response.getPayload();
    }

    /**
     * One blocking request over the socket. Server pushes share this stream, so anything that
     * is not the answer is skipped rather than mistaken for one.
     */
    private Response call(Command command, Map<String, String> params) throws Exception {
        long id = requestIds.incrementAndGet();
        codec.write(new Request(id, command, params));
        while (true) {
            Object frame = codec.read();
            if (frame instanceof Response response && response.getRequestId() == id) {
                return response;
            }
            if (frame == null) {
                throw new IOException("server closed the connection");
            }
        }
    }
}
