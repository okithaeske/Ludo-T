package server;

import app.port.Clock;
import app.port.GameRepository;
import net.ConnectionRegistry;

import java.io.IOException;
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

        // GameRepository.NO_OP until the database tier is built — the port exists, so adding
        // persistence later changes this one argument and nothing else.
        ServerAssembly assembly = new ServerAssembly(config, GameRepository.NO_OP, Clock.SYSTEM);
        assembly.start();

        ScheduledExecutorService metricsTicker =
                startMetricsBroadcast(assembly.getConnections(), config);

        System.out.println("[server] Ludo-T server listening on port " + assembly.getPort());
        System.out.println("[server] workers=" + config.workers()
                + " queueCapacity=" + config.queueCapacity());
        System.out.println("[server] press Ctrl+C to stop");

        awaitShutdown(assembly, metricsTicker);
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
    private static void awaitShutdown(ServerAssembly assembly, ScheduledExecutorService ticker)
            throws InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[server] shutting down...");
            ticker.shutdownNow();
            assembly.close();
            stopped.countDown();
            System.out.println("[server] stopped.");
        }, "shutdown"));
        stopped.await();
    }
}
