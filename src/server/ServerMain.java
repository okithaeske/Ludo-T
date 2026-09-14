package server;

import app.port.Clock;
import app.port.GameRepository;
import net.ConnectionRegistry;
import persistence.DatabaseConfig;
import persistence.JdbcGameRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The server application: one of the three processes this system runs as.
 *
 * <p>All wiring lives in {@link ServerAssembly}; this class only reads configuration, starts
 * the assembly, broadcasts load figures, and waits for Ctrl+C.
 *
 * <p>Run with {@code --port=5599 --workers=4 --queue=256}.
 */
public final class ServerMain {

    private ServerMain() {
        // Entry point only.
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerConfig config = ServerConfig.parse(args);
        GameRepository repository = openRepository(DatabaseConfig.parse(args));

        ServerAssembly assembly = new ServerAssembly(config, repository, Clock.SYSTEM);
        assembly.start();

        ScheduledExecutorService metricsTicker =
                startMetricsBroadcast(assembly.getConnections(), config);

        System.out.println("[server] Ludo-T server listening on port " + assembly.getPort());
        System.out.println("[server] workers=" + config.workers()
                + " queueCapacity=" + config.queueCapacity());
        System.out.println("[server] press Ctrl+C to stop");

        awaitShutdown(assembly, metricsTicker, repository);
    }

    /**
     * Connects to the database tier, or reports why it could not and carries on without one.
     *
     * <p>Deliberately never fatal. A missing database costs the History and Leaderboard tabs;
     * refusing to start would cost the entire demonstration, and the most likely cause is
     * simply that {@code run-db.ps1} has not been started yet. The line printed here tells the
     * operator exactly which of the two states they are in, so a silently empty History tab is
     * never a mystery.
     */
    private static GameRepository openRepository(DatabaseConfig dbConfig) {
        if (dbConfig.isDisabled()) {
            System.out.println("[server] database tier disabled (--db=off); history is not kept");
            return GameRepository.NO_OP;
        }
        try {
            JdbcGameRepository repository = JdbcGameRepository.connect(dbConfig);
            System.out.println("[server] database tier at " + dbConfig.url());
            return repository;
        } catch (SQLException e) {
            System.out.println("[server] no database tier: " + e.getMessage());
            System.out.println("[server] start it with run-db.ps1; running without history");
            return GameRepository.NO_OP;
        }
    }

    private static ScheduledExecutorService startMetricsBroadcast(ConnectionRegistry connections,
                                                                  ServerConfig config) {
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "metrics-broadcast");
            thread.setDaemon(true);
            return thread;
        });
        ticker.scheduleAtFixedRate(() -> {
            try {
                connections.broadcastMetrics();
            } catch (RuntimeException e) {
                // A broken client must never kill the ticker: if this escaped,
                // scheduleAtFixedRate would silently stop rescheduling for everyone.
                System.out.println("[server] metrics broadcast failed: " + e.getMessage());
            }
        }, config.metricsIntervalMillis(), config.metricsIntervalMillis(), TimeUnit.MILLISECONDS);
        return ticker;
    }

    /** Blocks until Ctrl+C. */
    private static void awaitShutdown(ServerAssembly assembly, ScheduledExecutorService ticker,
                                      GameRepository repository) throws InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[server] shutting down...");
            ticker.shutdownNow();
            assembly.close();
            // Closed after the assembly, so every session has finished handing over its rows
            // before the writer is asked to drain: the other order loses the last game.
            closeRepository(repository);
            stopped.countDown();
            System.out.println("[server] stopped.");
        }, "shutdown"));
        stopped.await();
    }

    /** {@link GameRepository#NO_OP} is not closeable; the JDBC one is. */
    private static void closeRepository(GameRepository repository) {
        if (!(repository instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            System.out.println("[server] database shutdown: " + e.getMessage());
        }
    }
}
