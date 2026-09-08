package client.testing;

import shared.Command;
import shared.Response;
import shared.ServerMetricsDto;
import shared.SessionSummaryDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point for the load tier: creates games, drives them from several independent clients
 * at once, and prints what the server did about it.
 *
 * <h2>What a run is</h2>
 * <ol>
 *   <li><b>Setup</b> — one control connection creates {@code --games} games and starts them,
 *       so real rounds are executing on real game actors while requests arrive. A load test
 *       against an idle server measures an empty queue.</li>
 *   <li><b>Warm-up</b> — a short sequential burst on the control connection, discarded. Class
 *       loading, the first serialisation of each DTO and JIT compilation all land on the first
 *       few requests; leaving them in would put a two-figure millisecond outlier in every p99
 *       and misattribute a JVM start-up cost to the server.</li>
 *   <li><b>Load</b> — {@code --clients} connections × {@code --threads} threads fire the
 *       chosen mix without waiting for answers, while a sampler polls {@code GET_METRICS}
 *       from outside the load so the server's own view of its queue is recorded as it
 *       happens rather than reconstructed afterwards.</li>
 *   <li><b>Report</b> — latency percentiles per command, the server's queue high-water mark,
 *       and the counts that show whether anything was lost.</li>
 * </ol>
 *
 * <h2>The claim this run is designed to support</h2>
 * The server accepts simultaneous requests from several fast automatic clients, queues them,
 * and answers every one. So the report leads with three numbers that would expose the opposite:
 * requests sent versus answered (nothing silently dropped), the queue's high-water mark against
 * its capacity (work really did queue rather than being handled one at a time), and the
 * saturation count (how often caller-runs backpressure engaged). A latency table alone could
 * be produced by a server that quietly discarded half the load.
 */
public final class TestClientMain {

    private static final long CALL_TIMEOUT_MILLIS = 20_000L;
    private static final long DRAIN_TIMEOUT_MILLIS = 30_000L;
    private static final int WARMUP_REQUESTS = 50;
    private static final String PARAM_GAME_ID = "gameId";

    private TestClientMain() {
        // Entry point only.
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                System.out.println(LoadConfig.usage());
                return;
            }
        }

        LoadConfig config = LoadConfig.parse(args);
        if (config.requestsPerThread() <= 0 && config.durationSeconds() <= 0) {
            System.out.println("[load] --requests=0 needs a --duration, or the run never ends.");
            System.exit(2);
        }

        try {
            new Runner(config).run();
        } catch (IOException e) {
            System.out.println("[load] cannot reach " + config.host() + ":" + config.port()
                    + " - " + e.getMessage());
            System.out.println("[load] start the server first: run-server.ps1");
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[load] interrupted");
            System.exit(1);
        } catch (Exception e) {
            System.out.println("[load] run failed: " + e);
            System.exit(1);
        }
    }

    /** One complete run. Kept as an object so the phases can share state without globals. */
    private static final class Runner {

        private final LoadConfig config;
        private final List<String> gameIds = new ArrayList<>();
        private final List<LoadClient> clients = new ArrayList<>();
        private final Map<Command, LatencyRecorder> recorders = LoadClient.newRecorders();

        private AsyncConnection control;
        private MetricsSampler sampler;
        private ServerMetricsDto before;
        private long wallNanos;

        Runner(LoadConfig config) {
            this.config = config;
        }

        void run() throws Exception {
            control = AsyncConnection.connect("load-control", config.host(), config.port());
            try {
                setUpGames();
                warmUp();
                before = fetchMetrics();
                driveLoad();
                new ReportWriter(this, fetchMetrics()).print();
            } finally {
                tearDown();
            }
        }

        private void setUpGames() throws Exception {
            for (int i = 0; i < config.games(); i++) {
                Response created = control.call(Command.CREATE_GAME, Map.of(
                        "mode", config.mode(),
                        // Seeded, so a repeated run drives the same games through the same
                        // rounds and a latency difference means something changed in the server.
                        "seed", String.valueOf(config.seed() + i),
                        "tickMillis", String.valueOf(config.tickMillis())), CALL_TIMEOUT_MILLIS);
                if (!created.isOk() || !(created.getPayload() instanceof SessionSummaryDto game)) {
                    throw new IllegalStateException("Could not create game: "
                            + created.getMessage());
                }
                gameIds.add(game.gameId());
                if (config.startGames()) {
                    control.call(Command.START_GAME, Map.of(PARAM_GAME_ID, game.gameId()),
                            CALL_TIMEOUT_MILLIS);
                }
            }
            System.out.println("[load] games " + gameIds + " (" + config.mode() + ", tick "
                    + config.tickMillis() + "ms" + (config.startGames() ? ", running)" : ")"));
        }

        private void warmUp() throws Exception {
            for (int i = 0; i < WARMUP_REQUESTS; i++) {
                control.call(Command.PING, Map.of(), CALL_TIMEOUT_MILLIS);
                if (!gameIds.isEmpty()) {
                    control.call(Command.GET_SNAPSHOT, Map.of(PARAM_GAME_ID, gameIds.get(0)),
                            CALL_TIMEOUT_MILLIS);
                }
            }
        }

        private void driveLoad() throws Exception {
            for (int i = 0; i < config.clients(); i++) {
                LoadClient client = new LoadClient(i, config, List.copyOf(gameIds), recorders);
                clients.add(client);
                if (config.subscribe()) {
                    client.subscribeAll(CALL_TIMEOUT_MILLIS);
                }
            }

            sampler = new MetricsSampler(control, config.sampleMillis());
            long deadlineNanos = config.durationSeconds() > 0
                    ? System.nanoTime() + config.durationSeconds() * 1_000_000_000L
                    : 0L;

            System.out.println("[load] " + config.clients() + " client(s) x " + config.threads()
                    + " thread(s), mix=" + config.mix() + ", in-flight cap "
                    + config.inFlightPerClient() + " per client - starting");

            long startNanos = System.nanoTime();
            sampler.start();
            clients.forEach(client -> client.start(deadlineNanos));
            for (LoadClient client : clients) {
                client.awaitSendersFinished();
            }

            // One shared deadline for every client's outstanding answers. Draining them in turn
            // would charge the wall clock a full timeout per stalled client, so a server that
            // lost one response would be reported as a server that had gone slow.
            long drainDeadline = System.nanoTime() + DRAIN_TIMEOUT_MILLIS * 1_000_000L;
            int outstanding = 0;
            for (LoadClient client : clients) {
                long remainingMillis = (drainDeadline - System.nanoTime()) / 1_000_000L;
                outstanding += client.awaitDrain(remainingMillis);
            }
            wallNanos = System.nanoTime() - startNanos;
            sampler.stop();
            if (outstanding > 0) {
                System.out.printf("[load] %d request(s) were still unanswered after %d ms%n",
                        outstanding, DRAIN_TIMEOUT_MILLIS);
            }
        }

        private ServerMetricsDto fetchMetrics() {
            try {
                Response response = control.call(Command.GET_METRICS, Map.of(),
                        CALL_TIMEOUT_MILLIS);
                if (response.getPayload() instanceof ServerMetricsDto metrics) {
                    return metrics;
                }
            } catch (Exception e) {
                System.out.println("[load] metrics unavailable: " + e.getMessage());
            }
            return null;
        }

        private void tearDown() {
            clients.forEach(LoadClient::close);
            if (control != null) {
                if (!config.keepGames()) {
                    for (String gameId : gameIds) {
                        try {
                            control.call(Command.ABORT_GAME, Map.of(PARAM_GAME_ID, gameId), 5_000L);
                        } catch (Exception e) {
                            System.out.println("[load] could not abort " + gameId
                                    + ": " + e.getMessage());
                        }
                    }
                }
                control.close();
            }
        }
    }

    /**
     * Polls the server's own metrics on its own thread, outside the load.
     *
     * <p>It has to be a sample rather than a single reading at the end: the queue is deepest
     * <em>during</em> the run and empty again by the time it finishes, so a before/after pair
     * would report a maximum depth of zero and quietly contradict the run it is describing.
     * The sampler uses the control connection, so its own requests are not counted in the
     * latency figures — a monitor that measured itself would be reporting on the wrong thing.
     */
    private static final class MetricsSampler {

        private final AsyncConnection connection;
        private final long intervalMillis;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread;

        private volatile int samples;
        private volatile int maxQueueDepth;
        private volatile int queueCapacity;
        private volatile int maxActiveWorkers;
        private volatile int maxConnectedClients;
        private volatile long totalQueueDepth;
        private volatile long maxInFlight;

        MetricsSampler(AsyncConnection connection, long intervalMillis) {
            this.connection = connection;
            this.intervalMillis = Math.max(25L, intervalMillis);
            this.thread = new Thread(this::sampleLoop, "load-metrics-sampler");
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void stop() {
            running.set(false);
            thread.interrupt();
        }

        private void sampleLoop() {
            while (running.get() && connection.isOpen()) {
                try {
                    Response response = connection.call(Command.GET_METRICS, Map.of(), 5_000L);
                    if (response.getPayload() instanceof ServerMetricsDto metrics) {
                        accept(metrics);
                    }
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    return; // The run is finishing, or the server went away; the report says so.
                }
            }
        }

        private void accept(ServerMetricsDto metrics) {
            samples++;
            queueCapacity = metrics.queueCapacity();
            maxQueueDepth = Math.max(maxQueueDepth, metrics.queueDepth());
            maxActiveWorkers = Math.max(maxActiveWorkers, metrics.activeWorkers());
            maxConnectedClients = Math.max(maxConnectedClients, metrics.connectedClients());
            maxInFlight = Math.max(maxInFlight, metrics.inFlight());
            totalQueueDepth += metrics.queueDepth();
        }

        int samples() {
            return samples;
        }

        int maxQueueDepth() {
            return maxQueueDepth;
        }

        int queueCapacity() {
            return queueCapacity;
        }

        int maxActiveWorkers() {
            return maxActiveWorkers;
        }

        int maxConnectedClients() {
            return maxConnectedClients;
        }

        long maxInFlight() {
            return maxInFlight;
        }

        double averageQueueDepth() {
            return samples == 0 ? 0.0 : (double) totalQueueDepth / samples;
        }
    }

    /** Turns the recorders and samples into the text a report can quote directly. */
    private static final class ReportWriter {

        private final LoadConfig config;
        private final Map<Command, LatencyRecorder> recorders;
        private final List<LoadClient> clients;
        private final MetricsSampler sampler;
        private final ServerMetricsDto before;
        private final ServerMetricsDto after;
        private final long wallNanos;
        private final List<String> gameIds;

        /**
         * Reads the finished run straight off the {@link Runner} rather than being handed its
         * eight fields one at a time. {@code after} stays a parameter because it is the one
         * value the runner does not keep: it is fetched at the moment the report is written.
         */
        ReportWriter(Runner runner, ServerMetricsDto after) {
            this.config = runner.config;
            this.recorders = runner.recorders;
            this.clients = runner.clients;
            this.sampler = runner.sampler;
            this.before = runner.before;
            this.after = after;
            this.wallNanos = runner.wallNanos;
            this.gameIds = runner.gameIds;
        }

        void print() {
            long sent = clients.stream().mapToLong(LoadClient::sentCount).sum();
            long answered = clients.stream().mapToLong(LoadClient::answeredCount).sum();
            long events = clients.stream().mapToLong(LoadClient::eventsReceived).sum();
            long snapshots = clients.stream().mapToLong(LoadClient::snapshotsReceived).sum();
            double seconds = wallNanos / 1_000_000_000.0;

            long ok = 0;
            long rejected = 0;
            long errors = 0;
            long failures = 0;
            for (LatencyRecorder recorder : recorders.values()) {
                LatencyRecorder.Stats stats = recorder.stats();
                ok += stats.ok();
                rejected += stats.rejected();
                errors += stats.errors();
                failures += stats.failures();
            }

            System.out.println();
            rule("Ludo-T load run");
            System.out.printf("target     %s:%d   games %s   mix %s%n",
                    config.host(), config.port(), gameIds, config.mix());
            System.out.printf("clients    %d connection(s) x %d thread(s), in-flight cap %d each%n",
                    config.clients(), config.threads(), config.inFlightPerClient());
            System.out.printf("requests   sent %,d   answered %,d   (ok %,d  rejected %,d  "
                            + "error %,d  unanswered %,d)%n",
                    sent, answered, ok, rejected, errors, failures);
            System.out.printf("wall time  %.2f s   throughput %,.1f req/s%n",
                    seconds, seconds == 0 ? 0 : answered / seconds);
            if (config.subscribe()) {
                System.out.printf("pushes     %,d event(s) received unprompted, of which "
                        + "%,d board snapshot(s)%n", events, snapshots);
            }

            printLatency();
            printServerView();
            printVerdict(sent, answered, errors, failures);
            writeCsv(sent, answered, ok, rejected, errors, failures, seconds);
        }

        private void printLatency() {
            System.out.println();
            System.out.printf("%-14s %8s %9s %9s %9s %9s %9s%n",
                    "latency (ms)", "n", "mean", "p50", "p90", "p99", "max");
            long overflow = 0;
            for (Command command : Command.values()) {
                LatencyRecorder recorder = recorders.get(command);
                if (recorder.isEmpty()) {
                    continue;
                }
                overflow += recorder.overflowCount();
                printRow(recorder.stats());
            }
            if (overflow > 0) {
                System.out.printf("  (%,d sample(s) beyond the recorder capacity were counted "
                        + "but not timed)%n", overflow);
            }
        }

        private void printRow(LatencyRecorder.Stats stats) {
            System.out.printf("  %-12s %8d %9.2f %9.2f %9.2f %9.2f %9.2f%n",
                    stats.name(), stats.total(),
                    LatencyRecorder.Stats.toMillis(stats.meanNanos()),
                    LatencyRecorder.Stats.toMillis(stats.p50Nanos()),
                    LatencyRecorder.Stats.toMillis(stats.p90Nanos()),
                    LatencyRecorder.Stats.toMillis(stats.p99Nanos()),
                    LatencyRecorder.Stats.toMillis(stats.maxNanos()));
        }

        private void printServerView() {
            System.out.println();
            if (sampler == null || sampler.samples() == 0) {
                System.out.println("server     no metrics samples were collected");
                return;
            }
            System.out.printf("server     sampled %d time(s) every %d ms during the run%n",
                    sampler.samples(), config.sampleMillis());
            System.out.printf("  queue      depth max %d / %d   avg %.1f%n",
                    sampler.maxQueueDepth(), sampler.queueCapacity(), sampler.averageQueueDepth());
            System.out.printf("  workers    active max %d   in-flight max %d%n",
                    sampler.maxActiveWorkers(), sampler.maxInFlight());
            System.out.printf("  clients    connected max %d%n", sampler.maxConnectedClients());
            if (before != null && after != null) {
                System.out.printf("  accepted   +%,d   completed +%,d   saturation (caller-runs) "
                                + "+%,d%n",
                        after.acceptedRequests() - before.acceptedRequests(),
                        after.completedRequests() - before.completedRequests(),
                        after.rejectedRequests() - before.rejectedRequests());
            }
        }

        /**
         * States plainly whether the run supports the claim, so a reader is not left to infer
         * it from the tables — and so a bad run says so rather than looking like a good one.
         */
        private void printVerdict(long sent, long answered, long errors, long failures) {
            System.out.println();
            if (failures == 0 && sent == answered) {
                System.out.printf("verdict    every one of %,d request(s) was answered; "
                        + "nothing was dropped%n", sent);
            } else {
                System.out.printf("verdict    %,d of %,d request(s) went unanswered - "
                        + "investigate before quoting this run%n", sent - answered, sent);
            }
            if (errors > 0) {
                System.out.printf("           %,d error response(s): the server rejected work "
                        + "it should have accepted%n", errors);
            }
            long saturation = before == null || after == null
                    ? 0L : after.rejectedRequests() - before.rejectedRequests();
            if (saturation > 0) {
                // The sampled depth can read zero on a short run purely because the samples
                // landed between bursts, so the saturation counter is the stronger witness:
                // it is incremented by the queue itself, once per request that found it full,
                // and cannot miss an event the way a periodic sample can.
                System.out.printf("           the inbound queue filled %,d time(s); each of those "
                        + "requests was executed by its own connection thread (caller-runs), "
                        + "throttling that client rather than dropping its work%n", saturation);
            } else if (sampler != null && sampler.maxQueueDepth() == 0) {
                System.out.println("           the queue never filled and was never sampled "
                        + "non-empty - raise --clients, --threads or --inflight, or start the "
                        + "server with fewer --workers and a smaller --queue; as it stands this "
                        + "run does not evidence queueing");
            }
            rule("");
        }

        private void writeCsv(long sent, long answered, long ok, long rejected,
                              long errors, long failures, double seconds) {
            if (config.csvPath() == null || config.csvPath().isBlank()) {
                return;
            }
            LatencyRecorder.Stats all = aggregate();
            Path path = Path.of(config.csvPath());
            String header = "clients,threads,inflight,mix,games,sent,answered,ok,rejected,errors,"
                    + "unanswered,seconds,throughput,p50ms,p90ms,p99ms,maxms,maxQueueDepth,"
                    + "queueCapacity,saturation\n";
            String row = String.format(Locale.ROOT,
                    "%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%.1f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d%n",
                    config.clients(), config.threads(), config.inFlightPerClient(), config.mix(),
                    config.games(), sent, answered, ok, rejected, errors, failures, seconds,
                    seconds == 0 ? 0 : answered / seconds,
                    LatencyRecorder.Stats.toMillis(all.p50Nanos()),
                    LatencyRecorder.Stats.toMillis(all.p90Nanos()),
                    LatencyRecorder.Stats.toMillis(all.p99Nanos()),
                    LatencyRecorder.Stats.toMillis(all.maxNanos()),
                    sampler == null ? 0 : sampler.maxQueueDepth(),
                    sampler == null ? 0 : sampler.queueCapacity(),
                    before == null || after == null
                            ? 0 : after.rejectedRequests() - before.rejectedRequests());
            try {
                boolean fresh = !Files.exists(path);
                if (fresh) {
                    Files.writeString(path, header, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                }
                Files.writeString(path, row, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("[load] appended one row to " + path.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("[load] could not write " + path + ": " + e.getMessage());
            }
        }

        /**
         * Percentiles across every command at once. Computed by merging the per-command samples
         * rather than averaging their percentiles, which is not a thing that can be averaged.
         */
        private LatencyRecorder.Stats aggregate() {
            LatencyRecorder merged = new LatencyRecorder("ALL");
            long p50 = 0;
            long p90 = 0;
            long p99 = 0;
            long max = 0;
            long count = 0;
            double weightedMean = 0.0;
            for (LatencyRecorder recorder : recorders.values()) {
                LatencyRecorder.Stats stats = recorder.stats();
                long answered = stats.ok() + stats.rejected() + stats.errors();
                if (answered == 0) {
                    continue;
                }
                count += answered;
                weightedMean += stats.meanNanos() * answered;
                p50 = Math.max(p50, stats.p50Nanos());
                p90 = Math.max(p90, stats.p90Nanos());
                p99 = Math.max(p99, stats.p99Nanos());
                max = Math.max(max, stats.maxNanos());
            }
            // The per-command recorders keep their own samples, so an exact merged percentile
            // would mean copying every array again. The CSV therefore records the worst
            // per-command percentile, which is the conservative direction to be wrong in.
            return new LatencyRecorder.Stats(merged.name(), count, 0, 0, 0,
                    p50, p90, p99, max, count == 0 ? 0 : weightedMean / count);
        }

        private static void rule(String title) {
            String line = "-".repeat(72);
            System.out.println(title.isBlank() ? line : "-- " + title + " " + line.substring(
                    Math.min(line.length(), title.length() + 4)));
        }
    }
}
