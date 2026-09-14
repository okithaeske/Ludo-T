package test.integration;

import app.model.FinishedGame;
import app.model.GameSnapshot;
import app.model.PlayerView;
import app.model.SessionState;
import app.model.SessionSummary;
import app.model.StrategyRanking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import persistence.DatabaseConfig;
import persistence.JdbcGameRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The database tier, exercised against a real H2 running the project's real
 * {@code db/schema.sql}.
 *
 * <h2>Why a real database and not a mock</h2>
 * A mocked {@code GameRepository} would prove the interface is called and nothing else. Every
 * defect this tier can actually have — a column too narrow, a null the schema rejects, a
 * foreign key ordered wrong, a view whose aggregation is not what the leaderboard expects —
 * lives in the SQL, and only a real database can fail on it. So this runs {@code schema.sql}
 * itself: if that file and the Java disagree, these tests break rather than the demonstration.
 *
 * <p>The H2 used here is in-memory, so no test touches {@code db/ludo.mv.db} and the tutor's
 * seeded database is never disturbed by a test run.
 */
@DisplayName("Persistence tier")
class PersistenceTierTest {

    private static final AtomicInteger DB_NAMES = new AtomicInteger();

    private DatabaseConfig config;
    private JdbcGameRepository repository;
    private String url;

    /** Skips the whole class when lib\h2.jar is absent rather than failing the suite. */
    @BeforeEach
    void setUp() throws Exception {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            Assumptions.abort("lib\\h2.jar not present; run run-db.ps1 -Init once to fetch it");
        }

        // A distinct in-memory database per test, kept alive by DB_CLOSE_DELAY while this
        // test holds no connection of its own.
        url = "jdbc:h2:mem:ludotest" + DB_NAMES.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        applySchema();

        config = new DatabaseConfig(url, "sa", "", 1000);
        repository = JdbcGameRepository.connect(config);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (repository != null) {
            repository.close();
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    /** Runs the project's own schema file, so the test and the demonstration share one truth. */
    private void applySchema() throws Exception {
        Path schema = Path.of("db", "schema.sql");
        Assumptions.assumeTrue(Files.exists(schema), "db/schema.sql not found");
        String sql = Files.readString(schema);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** Blocks until the async writer has drained, so assertions do not race it. */
    private void awaitWrites(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (repository.writtenCount() >= expected && repository.queueDepth() == 0) {
                return;
            }
            Thread.sleep(20);
        }
        Assertions.fail("writer did not drain: wrote " + repository.writtenCount()
                + " of " + expected + ", " + repository.queueDepth() + " still queued");
    }

    private static GameSnapshot finishedGame(String gameId, List<String> order) {
        List<PlayerView> players = List.of(
                player("RED", "Red", "AggressiveStrategy", 4, 7),
                player("GREEN", "Green", "BlockerStrategy", 3, 2),
                player("YELLOW", "Yellow", "RacerStrategy", 4, 1),
                player("BLUE", "Blue", "MysteryHunterStrategy", 2, 4));
        return new GameSnapshot(gameId, "LUDO_T", SessionState.FINISHED, 57,
                players, -1, 0, order, true);
    }

    private static PlayerView player(String colour, String name, String strategy,
                                     int home, int captures) {
        return new PlayerView(colour, name, strategy, List.of(), 0, 0, home, captures, home == 4);
    }

    @Nested
    @DisplayName("Writing")
    class Writing {

        @Test
        @DisplayName("should_storeGameAndPlayers_when_aGameFinishes")
        void should_storeGameAndPlayers_when_aGameFinishes() throws Exception {
            repository.recordResult(finishedGame("g1", List.of("YELLOW", "RED", "BLUE", "GREEN")));
            awaitWrites(1);

            List<FinishedGame> stored = repository.findRecentResults(10);
            Assertions.assertEquals(1, stored.size());

            FinishedGame game = stored.get(0);
            Assertions.assertEquals("g1", game.gameId());
            Assertions.assertEquals("LUDO_T", game.mode());
            Assertions.assertEquals(57, game.rounds());
            Assertions.assertEquals("YELLOW", game.winnerColour());
            // The winner's strategy is resolved from the players, not stored by the caller.
            Assertions.assertEquals("RacerStrategy", game.winnerStrategy());
            Assertions.assertEquals(List.of("YELLOW", "RED", "BLUE", "GREEN"),
                    game.finishingOrder());
        }

        @Test
        @DisplayName("should_derivePlaceFromFinishingOrder_when_resultIsStored")
        void should_derivePlaceFromFinishingOrder_when_resultIsStored() throws Exception {
            repository.recordResult(finishedGame("g1", List.of("YELLOW", "RED", "BLUE", "GREEN")));
            awaitWrites(1);

            Assertions.assertEquals(1, placeOf("g1", "YELLOW"));
            Assertions.assertEquals(2, placeOf("g1", "RED"));
            Assertions.assertEquals(4, placeOf("g1", "GREEN"));
        }

        @Test
        @DisplayName("should_storeNullPlace_when_aPlayerNeverFinished")
        void should_storeNullPlace_when_aPlayerNeverFinished() throws Exception {
            // Only two players home; the other two must not be given a place they never took.
            repository.recordResult(finishedGame("g1", List.of("YELLOW", "RED")));
            awaitWrites(1);

            Assertions.assertEquals(2, placeOf("g1", "RED"));
            Assertions.assertNull(placeOf("g1", "GREEN"));
            Assertions.assertNull(placeOf("g1", "BLUE"));
        }

        @Test
        @DisplayName("should_storeNoWinner_when_theGameEndedWithNobodyHome")
        void should_storeNoWinner_when_theGameEndedWithNobodyHome() throws Exception {
            repository.recordResult(finishedGame("g1", List.of()));
            awaitWrites(1);

            FinishedGame game = repository.findRecentResults(10).get(0);
            Assertions.assertNull(game.winnerColour());
            Assertions.assertTrue(game.finishingOrder().isEmpty());
        }

        @Test
        @DisplayName("should_storeSessionAndEvents_when_aGameRuns")
        void should_storeSessionAndEvents_when_aGameRuns() throws Exception {
            repository.recordSessionCreated(
                    new SessionSummary("g1", "LUDO_T", 42L, SessionState.RUNNING, 0, 0, 250L));
            repository.recordEvent("g1", 1, "red rolls 6.");
            repository.recordEvent("g1", 2, "green captures red at 14.");
            awaitWrites(3);

            Assertions.assertEquals(1, countOf("SELECT COUNT(*) FROM game_session"));
            Assertions.assertEquals(2, countOf("SELECT COUNT(*) FROM game_event"));
        }

        @Test
        @DisplayName("should_storeNullSeed_when_theGameWasUnseeded")
        void should_storeNullSeed_when_theGameWasUnseeded() throws Exception {
            repository.recordSessionCreated(
                    new SessionSummary("g1", "LUDO_T", null, SessionState.RUNNING, 0, 0, 250L));
            awaitWrites(1);

            Assertions.assertEquals(1,
                    countOf("SELECT COUNT(*) FROM game_session WHERE seed IS NULL"));
        }

        @Test
        @DisplayName("should_notThrow_when_aMessageIsLongerThanItsColumn")
        void should_notThrow_when_aMessageIsLongerThanItsColumn() throws Exception {
            // The column is VARCHAR(512); an over-long line must be trimmed, not lost, and
            // must certainly not fail on the writer thread where nobody would see it.
            repository.recordEvent("g1", 1, "x".repeat(4_000));
            awaitWrites(1);

            Assertions.assertEquals(1, countOf("SELECT COUNT(*) FROM game_event"));
            Assertions.assertEquals(0, repository.failedCount());
        }
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("should_returnGamesNewestFirst_when_severalHaveFinished")
        void should_returnGamesNewestFirst_when_severalHaveFinished() throws Exception {
            repository.recordResult(finishedGame("g1", List.of("RED", "GREEN", "YELLOW", "BLUE")));
            awaitWrites(1);
            Thread.sleep(20); // distinct finished_at timestamps
            repository.recordResult(finishedGame("g2", List.of("BLUE", "RED", "GREEN", "YELLOW")));
            awaitWrites(2);

            List<FinishedGame> stored = repository.findRecentResults(10);
            Assertions.assertEquals("g2", stored.get(0).gameId());
            Assertions.assertEquals("g1", stored.get(1).gameId());
        }

        @Test
        @DisplayName("should_honourTheLimit_when_moreGamesExistThanAsked")
        void should_honourTheLimit_when_moreGamesExistThanAsked() throws Exception {
            for (int i = 1; i <= 5; i++) {
                repository.recordResult(
                        finishedGame("g" + i, List.of("RED", "GREEN", "YELLOW", "BLUE")));
            }
            awaitWrites(5);

            Assertions.assertEquals(2, repository.findRecentResults(2).size());
        }

        @Test
        @DisplayName("should_countWinsPerStrategy_when_theLeaderboardIsRead")
        void should_countWinsPerStrategy_when_theLeaderboardIsRead() throws Exception {
            // Yellow (Racer) wins twice, Red (Aggressive) once.
            repository.recordResult(finishedGame("g1", List.of("YELLOW", "RED", "BLUE", "GREEN")));
            repository.recordResult(finishedGame("g2", List.of("YELLOW", "BLUE", "RED", "GREEN")));
            repository.recordResult(finishedGame("g3", List.of("RED", "YELLOW", "BLUE", "GREEN")));
            awaitWrites(3);

            List<StrategyRanking> board = repository.findStrategyLeaderboard();
            Assertions.assertEquals(4, board.size(), "one row per strategy that has played");

            // Ordered by wins descending by the view's ORDER BY.
            Assertions.assertEquals("RacerStrategy", board.get(0).strategy());
            Assertions.assertEquals(2, board.get(0).wins());
            Assertions.assertEquals(3, board.get(0).gamesPlayed());

            StrategyRanking blocker = board.stream()
                    .filter(row -> row.strategy().equals("BlockerStrategy"))
                    .findFirst().orElseThrow();
            Assertions.assertEquals(0, blocker.wins());
            Assertions.assertEquals(0.0, blocker.winRate());
        }

        @Test
        @DisplayName("should_returnEmpty_when_nothingHasFinished")
        void should_returnEmpty_when_nothingHasFinished() {
            Assertions.assertTrue(repository.findRecentResults(10).isEmpty());
            Assertions.assertTrue(repository.findStrategyLeaderboard().isEmpty());
        }
    }

    @Nested
    @DisplayName("Not blocking the caller")
    class NotBlocking {

        @Test
        @DisplayName("should_dropRatherThanBlock_when_theWriteQueueIsFull")
        void should_dropRatherThanBlock_when_theWriteQueueIsFull() throws Exception {
            // The contract this tier is built on: a game must never wait for the database.
            // A queue of one, flooded, has to shed rows and return immediately rather than
            // make the calling thread wait for a drain.
            JdbcGameRepository tiny = JdbcGameRepository.connect(
                    new DatabaseConfig(url, "sa", "", 1));
            try {
                long startNanos = System.nanoTime();
                for (int i = 0; i < 5_000; i++) {
                    tiny.recordEvent("g1", i, "line " + i);
                }
                long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

                Assertions.assertTrue(elapsedMillis < 2_000L,
                        "5,000 offers took " + elapsedMillis + "ms; the caller was blocked");
                Assertions.assertTrue(tiny.droppedCount() > 0,
                        "a queue of 1 flooded with 5,000 rows must drop some");
                // Every row is written, dropped, or still queued — never lost silently. The
                // tolerance of one is real and not slack: the writer takes a row off the
                // queue before it counts it as written, so a sample taken mid-flight sees it
                // in neither bucket. Anything more than one missing would be a lost row.
                long accountedFor = tiny.writtenCount() + tiny.droppedCount() + tiny.queueDepth();
                Assertions.assertTrue(accountedFor >= 4_999L && accountedFor <= 5_000L,
                        "rows unaccounted for: wrote " + tiny.writtenCount()
                                + ", dropped " + tiny.droppedCount()
                                + ", queued " + tiny.queueDepth()
                                + " = " + accountedFor + " of 5000");
            } finally {
                tiny.close();
            }
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should_useDefaults_when_noFlagsAreGiven")
        void should_useDefaults_when_noFlagsAreGiven() {
            DatabaseConfig parsed = DatabaseConfig.parse(new String[0]);
            Assertions.assertEquals(DatabaseConfig.DEFAULT_URL, parsed.url());
            Assertions.assertFalse(parsed.isDisabled());
        }

        @Test
        @DisplayName("should_beDisabled_when_dbIsOff")
        void should_beDisabled_when_dbIsOff() {
            Assertions.assertTrue(DatabaseConfig.parse(new String[] {"--db=off"}).isDisabled());
            Assertions.assertTrue(DatabaseConfig.parse(new String[] {"--db="}).isDisabled());
        }

        @Test
        @DisplayName("should_readEveryFlag_when_allAreGiven")
        void should_readEveryFlag_when_allAreGiven() {
            DatabaseConfig parsed = DatabaseConfig.parse(new String[] {
                    "--db=jdbc:h2:mem:x", "--dbuser=bob", "--dbpassword=hunter2",
                    "--dbqueue=99", "--unrelated=ignored"});
            Assertions.assertEquals("jdbc:h2:mem:x", parsed.url());
            Assertions.assertEquals("bob", parsed.user());
            Assertions.assertEquals("hunter2", parsed.password());
            Assertions.assertEquals(99, parsed.queueCapacity());
        }

        @Test
        @DisplayName("should_keepTheDefault_when_theQueueSizeIsNotANumber")
        void should_keepTheDefault_when_theQueueSizeIsNotANumber() {
            DatabaseConfig parsed = DatabaseConfig.parse(new String[] {"--dbqueue=lots"});
            Assertions.assertEquals(DatabaseConfig.DEFAULT_QUEUE_CAPACITY, parsed.queueCapacity());
        }
    }

    @Test
    @DisplayName("should_refuseToConnect_when_theSchemaHasNotBeenApplied")
    void should_refuseToConnect_when_theSchemaHasNotBeenApplied() throws SQLException {
        // Failing here, at start-up, is the point: the alternative is every insert failing
        // silently on the writer thread and the History tab being mysteriously empty.
        String bare = "jdbc:h2:mem:bare" + DB_NAMES.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> JdbcGameRepository.connect(new DatabaseConfig(bare, "sa", "", 10)));
        Assertions.assertTrue(thrown.getMessage().contains("not initialised"),
                "the message should tell the operator to run run-db.ps1 -Init, got: "
                        + thrown.getMessage());
    }

    // ── Helpers that read the database directly, to check what the SQL really did ──

    private Integer placeOf(String gameId, String colour) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT place FROM player_result WHERE game_id = '" + gameId
                             + "' AND colour = '" + colour + "'")) {
            Assertions.assertTrue(rows.next(), "no player_result row for " + colour);
            int place = rows.getInt("place");
            return rows.wasNull() ? null : place;
        }
    }

    private int countOf(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            Assertions.assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
