package persistence;

import app.model.FinishedGame;
import app.model.GameSnapshot;
import app.model.PlayerView;
import app.model.SessionSummary;
import app.model.StrategyRanking;
import app.port.GameRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@link GameRepository} implemented against H2, running in its own process.
 *
 * <h2>Why every write goes through a queue</h2>
 * {@code recordSessionCreated}, {@code recordEvent} and {@code recordResult} are all called
 * from a game's actor thread while it is playing a round. A JDBC call is a network round trip
 * to another process — hundreds of microseconds when the database is healthy, unbounded when
 * it is not. Calling it inline would make the game's round rate a function of database
 * latency, and a database that stopped answering would freeze every game that touched it.
 *
 * <p>So a write is turned into a {@link Write} and offered to a bounded queue; one writer
 * thread drains it. The actor's cost is an {@code offer} on an {@link ArrayBlockingQueue} and
 * nothing else. This is the same shape as the server's inbound {@code RequestQueue} and each
 * game's mailbox — the third instance of the same answer in this system, which is itself worth
 * noting in the report: bounded queue plus dedicated consumer is how every "must not block the
 * caller" boundary here is solved.
 *
 * <h2>Why the queue drops instead of applying backpressure</h2>
 * It is the one place in the system that does. The request queue makes the caller run the work
 * rather than lose it, because losing a client's command is a visible failure. Here the
 * opposite is true: the payload is history, and blocking a game to record what it just did
 * would sacrifice the thing being demonstrated to protect a row in a table nobody is reading.
 * So {@link #offer} uses non-blocking {@code offer()} and counts what it could not take. The
 * count is printed at shutdown rather than hidden, because a silently lossy store is worse
 * than a loudly lossy one.
 *
 * <h2>Threading</h2>
 * <pre>
 *   any session actor  →  offer() → queue        (never blocks, may drop)
 *   db-writer          →  drains the queue, owns ONE Connection, reconnects on failure
 *   request workers    →  findRecentResults / findStrategyLeaderboard, own Connection each
 * </pre>
 * The writer's connection is never touched by another thread, so no JDBC object here is
 * shared — which is what makes the whole class lock-free despite being written to from every
 * game at once. Reads open their own short-lived connection rather than borrow the writer's:
 * a {@code Connection} is not thread-safe, and a read that waited for the writer to be idle
 * would reintroduce exactly the coupling the queue exists to remove.
 */
public final class JdbcGameRepository implements GameRepository, AutoCloseable {

    /** One queued database write. Applied on the writer thread, never on a game's thread. */
    @FunctionalInterface
    private interface Write {
        void apply(Connection connection) throws SQLException;
    }

    private static final String INSERT_SESSION = """
            INSERT INTO game_session (game_id, mode, seed, tick_millis)
            VALUES (?, ?, ?, ?)""";

    private static final String INSERT_EVENT = """
            INSERT INTO game_event (game_id, round, message)
            VALUES (?, ?, ?)""";

    private static final String INSERT_RESULT = """
            INSERT INTO game_result
                (game_id, mode, rounds, winner_colour, winner_strategy, finishing_order)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_PLAYER = """
            INSERT INTO player_result
                (game_id, colour, player_name, strategy, pieces_home, captures, finished, place)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String SELECT_RESULTS = """
            SELECT game_id, mode, rounds, winner_colour, winner_strategy,
                   finishing_order, finished_at
            FROM game_result
            ORDER BY finished_at DESC
            LIMIT ?""";

    private static final String SELECT_LEADERBOARD = """
            SELECT strategy, games_played, wins, total_captures, avg_pieces_home
            FROM strategy_leaderboard
            ORDER BY wins DESC, total_captures DESC""";

    /** Longest a message may be before it is trimmed to fit the column. */
    private static final int MESSAGE_LIMIT = 512;

    private static final long SHUTDOWN_GRACE_SECONDS = 5L;

    private final DatabaseConfig config;
    private final BlockingQueue<Write> pending;
    private final Thread writer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private JdbcGameRepository(DatabaseConfig config) {
        this.config = config;
        this.pending = new ArrayBlockingQueue<>(config.queueCapacity());
        this.writer = new Thread(this::writeLoop, "db-writer");
        this.writer.setDaemon(true);
    }

    /**
     * Connects, verifies the schema is present, and starts the writer thread.
     *
     * @throws SQLException if the database process is not reachable or has not been
     *                      initialised — the caller decides whether that is fatal.
     *                      {@link server.ServerMain} treats it as a warning and falls back to
     *                      {@link GameRepository#NO_OP}, so a forgotten {@code run-db.ps1}
     *                      never blocks a demonstration.
     */
    public static JdbcGameRepository connect(DatabaseConfig config) throws SQLException {
        JdbcGameRepository repository = new JdbcGameRepository(config);
        try (Connection connection = repository.openConnection()) {
            repository.verifySchema(connection);
        }
        repository.writer.start();
        return repository;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    /**
     * Fails fast when the tables are missing, rather than letting every insert fail silently
     * on the writer thread where nobody would see it until the History tab came up empty.
     */
    private void verifySchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("SELECT 1 FROM strategy_leaderboard")) {
            // Reaching here means schema.sql has been applied.
        } catch (SQLException e) {
            throw new SQLException("The database is reachable but not initialised. "
                    + "Run: .\\run-db.ps1 -Init", e);
        }
    }

    // ── Writes: called on game actor threads, must return immediately ────────

    @Override
    public void recordSessionCreated(SessionSummary summary) {
        offer(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SESSION)) {
                statement.setString(1, summary.gameId());
                statement.setString(2, summary.mode());
                setNullableLong(statement, 3, summary.seed());
                statement.setLong(4, summary.tickMillis());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void recordEvent(String gameId, int round, String message) {
        String trimmed = message == null ? "" : message.length() > MESSAGE_LIMIT
                ? message.substring(0, MESSAGE_LIMIT)
                : message;
        offer(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
                statement.setString(1, gameId);
                statement.setInt(2, round);
                statement.setString(3, trimmed);
                statement.executeUpdate();
            }
        });
    }

    /**
     * Stores a finished game and its four players as one transaction, so the
     * {@code player_result} foreign key can never see a half-written parent and the History
     * tab can never show a game with two of its players missing.
     */
    @Override
    public void recordResult(GameSnapshot snapshot) {
        offer(connection -> writeResult(connection, snapshot));
    }

    private void writeResult(Connection connection, GameSnapshot snapshot) throws SQLException {
        List<String> order = snapshot.finishingOrder();
        String winner = order.isEmpty() ? null : order.get(0);

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_RESULT)) {
                statement.setString(1, snapshot.gameId());
                statement.setString(2, snapshot.mode());
                statement.setInt(3, snapshot.round());
                statement.setString(4, winner);
                statement.setString(5, strategyOf(snapshot, winner));
                statement.setString(6, String.join(",", order));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_PLAYER)) {
                for (PlayerView player : snapshot.players()) {
                    int place = order.indexOf(player.colour());
                    statement.setString(1, snapshot.gameId());
                    statement.setString(2, player.colour());
                    statement.setString(3, player.name());
                    statement.setString(4, player.strategy());
                    statement.setInt(5, player.piecesHome());
                    statement.setInt(6, player.totalCaptures());
                    statement.setBoolean(7, player.finished());
                    // indexOf returns -1 for a player that never finished; the column is
                    // nullable precisely so that "did not finish" is not stored as a place.
                    setNullableInt(statement, 8, place < 0 ? null : place + 1);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String strategyOf(GameSnapshot snapshot, String colour) {
        if (colour == null) {
            return null;
        }
        for (PlayerView player : snapshot.players()) {
            if (player.colour().equals(colour)) {
                return player.strategy();
            }
        }
        return null;
    }

    /** The whole cost a game pays for persistence: one non-blocking offer. */
    private void offer(Write write) {
        if (!running.get()) {
            return;
        }
        if (!pending.offer(write)) {
            dropped.incrementAndGet();
        }
    }

    // ── Reads: called on request-worker threads, may block ───────────────────

    @Override
    public List<FinishedGame> findRecentResults(int limit) {
        List<FinishedGame> results = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_RESULTS)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(toFinishedGame(rows));
                }
            }
        } catch (SQLException e) {
            // A read failure must not propagate as a server error: the History tab showing
            // nothing is a far better outcome than a command failing because the database
            // process was restarted mid-demonstration.
            System.out.println("[db] could not read results: " + e.getMessage());
        }
        return results;
    }

    private static FinishedGame toFinishedGame(ResultSet rows) throws SQLException {
        String order = rows.getString("finishing_order");
        Timestamp finishedAt = rows.getTimestamp("finished_at");
        return new FinishedGame(
                rows.getString("game_id"),
                rows.getString("mode"),
                rows.getInt("rounds"),
                rows.getString("winner_colour"),
                rows.getString("winner_strategy"),
                order == null || order.isBlank() ? List.of() : Arrays.asList(order.split(",")),
                finishedAt == null ? 0L : finishedAt.getTime());
    }

    @Override
    public List<StrategyRanking> findStrategyLeaderboard() {
        List<StrategyRanking> rankings = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SELECT_LEADERBOARD)) {
            while (rows.next()) {
                rankings.add(new StrategyRanking(
                        rows.getString("strategy"),
                        rows.getInt("games_played"),
                        rows.getInt("wins"),
                        rows.getInt("total_captures"),
                        rows.getDouble("avg_pieces_home")));
            }
        } catch (SQLException e) {
            System.out.println("[db] could not read leaderboard: " + e.getMessage());
        }
        return rankings;
    }

    // ── The writer thread ────────────────────────────────────────────────────

    private void writeLoop() {
        Connection connection = null;
        try {
            while (running.get() || !pending.isEmpty()) {
                Write write = pending.poll(200, TimeUnit.MILLISECONDS);
                if (write == null) {
                    continue;
                }
                connection = ensureConnected(connection);
                if (connection == null) {
                    // Still down; the row is lost rather than retried forever. Counted, so
                    // the shutdown line tells the truth about what was stored.
                    dropped.incrementAndGet();
                    continue;
                }
                connection = applyWrite(connection, write);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(connection);
        }
    }

    private Connection applyWrite(Connection connection, Write write) {
        try {
            write.apply(connection);
            written.incrementAndGet();
            return connection;
        } catch (SQLException e) {
            failed.incrementAndGet();
            if (failed.get() == 1 || failed.get() % 100 == 0) {
                System.out.println("[db] write failed (" + failed.get() + " so far): "
                        + e.getMessage());
            }
            // Drop the connection so the next write reconnects: a connection that has failed
            // once is usually a connection to a database process that has gone away.
            closeQuietly(connection);
            return null;
        }
    }

    private Connection ensureConnected(Connection connection) {
        if (connection != null) {
            return connection;
        }
        try {
            return openConnection();
        } catch (SQLException e) {
            return null;
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // Nothing useful to do while shutting down.
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    // ── Reporting and shutdown ───────────────────────────────────────────────

    public long writtenCount() {
        return written.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public int queueDepth() {
        return pending.size();
    }

    /**
     * Stops accepting writes and gives the writer a few seconds to drain what is already
     * queued, so the last game of a demonstration is stored rather than lost at Ctrl+C.
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            writer.join(TimeUnit.SECONDS.toMillis(SHUTDOWN_GRACE_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writer.interrupt();
        System.out.println("[db] stored " + written.get() + " row(s)"
                + (dropped.get() > 0 ? ", dropped " + dropped.get() : "")
                + (failed.get() > 0 ? ", " + failed.get() + " failed" : "")
                + (pending.isEmpty() ? "" : ", " + pending.size() + " never written"));
    }
}
