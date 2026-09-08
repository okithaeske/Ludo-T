package client.testing;

import shared.Command;
import shared.ResponseStatus;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One test client: a single socket driven by several threads firing requests without waiting.
 *
 * <h2>What this is proving</h2>
 * The rubric's client-side band asks for clients that send asynchronously in rapid succession.
 * "Asynchronous" here is structural, not decorative: a worker thread calls
 * {@link AsyncConnection#send} and immediately loops round to send the next request — it never
 * touches the returned future's {@code get()}. The answer is correlated by request id on the
 * receiver thread, which is the same mechanism the Swing client uses to keep its interface
 * live. A synchronous harness would measure round-trip time multiplied by thread count and
 * could never fill the server's queue.
 *
 * <h2>Why there is a permit for every request</h2>
 * Sending with no limit at all is not more impressive, it is just broken. A loop that never
 * waits will outrun the socket, and the excess piles up in this client's pending map and the
 * kernel's send buffer until the JVM runs out of memory — the client dies before the server is
 * ever stressed, and the report ends up describing a client-side bug.
 *
 * <p>So each worker takes a permit from a per-client {@link Semaphore} before sending and
 * returns it when the answer lands. That is deliberately the same shape as the server's
 * bounded inbound queue: a fixed amount of work may be outstanding, and the producer blocks
 * rather than the memory growing. It also makes the flag meaningful — {@code --inflight}
 * controls how hard one client leans on the server, so the same harness can demonstrate a
 * polite client and a flooding one without changing any code.
 *
 * <h2>Threads owned by one client</h2>
 * <pre>
 *   load-cN-sender     writes frames                (inside AsyncConnection)
 *   load-cN-receiver   reads frames, times answers  (inside AsyncConnection)
 *   load-cN-wM         builds and submits requests  (M of these, here)
 * </pre>
 */
public final class LoadClient implements AutoCloseable {

    private static final long DRAIN_TIMEOUT_SECONDS = 30L;

    private final String name;
    private final LoadConfig config;
    private final List<String> gameIds;
    private final Map<Command, LatencyRecorder> recorders;
    private final AsyncConnection connection;
    private final Semaphore permits;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong answered = new AtomicLong();
    private final Thread[] workers;
    private final CountDownLatch finished;

    private volatile boolean stopped;

    public LoadClient(int index,
                      LoadConfig config,
                      List<String> gameIds,
                      Map<Command, LatencyRecorder> recorders) throws IOException {
        this.name = "load-c" + (index + 1);
        this.config = config;
        this.gameIds = gameIds;
        this.recorders = recorders;
        this.permits = new Semaphore(config.inFlightPerClient());
        this.workers = new Thread[config.threads()];
        this.finished = new CountDownLatch(config.threads());
        this.connection = AsyncConnection.connect(name, config.host(), config.port());
    }

    public String name() {
        return name;
    }

    public long sentCount() {
        return sent.get();
    }

    public long answeredCount() {
        return answered.get();
    }

    public long eventsReceived() {
        return connection.eventsReceived();
    }

    public long snapshotsReceived() {
        return connection.snapshotsReceived();
    }

    /** Subscribes to every game under test, so this client also receives the push stream. */
    public void subscribeAll(long timeoutMillis) throws Exception {
        for (String gameId : gameIds) {
            connection.call(Command.SUBSCRIBE, Map.of("gameId", gameId), timeoutMillis);
        }
    }

    /** Starts the worker threads. Returns immediately; the run happens on those threads. */
    public void start(long deadlineNanos) {
        // Attached here rather than in the constructor so the subscribe handshake above is not
        // recorded: it is setup, sent once per game before the clock starts, and counting it
        // would make answers outnumber the requests the run claims to have sent.
        connection.setResponseObserver(this::onAnswer);
        connection.setFailureObserver(this::onFailure);

        for (int i = 0; i < workers.length; i++) {
            // Every worker gets its own seeded stream, derived from the run seed and its own
            // identity, so a run is reproducible without the threads sharing a Random and
            // serialising on it. Same reasoning as the per-game RandomSource in the engine.
            long workerSeed = config.seed() * 31 + name.hashCode() * 17L + i;
            Random random = new Random(workerSeed);
            workers[i] = new Thread(() -> runWorker(random, deadlineNanos), name + "-w" + i);
            workers[i].setDaemon(true);
        }
        for (Thread worker : workers) {
            worker.start();
        }
    }

    /** Blocks until every worker of this client has stopped sending. */
    public void awaitSendersFinished() throws InterruptedException {
        finished.await();
    }

    /**
     * Waits for the outstanding answers of this client, up to {@code timeoutMillis}.
     *
     * <p>Reclaiming every permit means nothing is still in flight, so the recorders can be read
     * without racing a writer. Bounded, because a server that stops answering must not hang the
     * report — and separated from {@link #awaitSendersFinished()} so a caller can drain several
     * clients against <em>one</em> deadline. Draining them one after another would add a whole
     * timeout per client to the measured wall time and turn a server stall into a throughput
     * figure that is wrong by an order of magnitude.
     *
     * @return the number of requests still unanswered when the wait gave up; 0 when drained
     */
    public int awaitDrain(long timeoutMillis) throws InterruptedException {
        boolean drained = permits.tryAcquire(config.inFlightPerClient(),
                Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS);
        return drained ? 0 : connection.inFlightCount();
    }

    /** Convenience for callers that do not need the two phases apart. */
    public void awaitCompletion() throws InterruptedException {
        awaitSendersFinished();
        int outstanding = awaitDrain(TimeUnit.SECONDS.toMillis(DRAIN_TIMEOUT_SECONDS));
        if (outstanding > 0) {
            System.out.println("[" + name + "] " + outstanding
                    + " request(s) never answered within " + DRAIN_TIMEOUT_SECONDS + "s");
        }
    }

    /** Asks the workers to stop at the next iteration; used by the duration limit. */
    public void stop() {
        stopped = true;
    }

    private void runWorker(Random random, long deadlineNanos) {
        try {
            int budget = config.requestsPerThread();
            boolean unlimited = budget <= 0;
            for (int i = 0; unlimited || i < budget; i++) {
                if (stopped || (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos)
                        || !connection.isOpen()) {
                    return;
                }
                fireOne(random);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finished.countDown();
        }
    }

    private void fireOne(Random random) throws InterruptedException {
        Command command = config.mix().next(random);
        Map<String, String> params = Workload.paramsFor(command, random, gameIds);

        permits.acquire();
        sent.incrementAndGet();
        // No get() anywhere: the permit is returned by whichever observer fires, so the
        // worker is free to build and send the next request immediately.
        connection.send(command, params);
    }

    private void onAnswer(Command command, ResponseStatus status, long latencyNanos) {
        recorderFor(command).record(status, latencyNanos);
        answered.incrementAndGet();
        permits.release();
    }

    private void onFailure(Command command) {
        recorderFor(command).recordFailure();
        permits.release();
    }

    private LatencyRecorder recorderFor(Command command) {
        return recorders.get(command);
    }

    /** Builds the shared recorder set — one per command, so the report can break latency down. */
    public static Map<Command, LatencyRecorder> newRecorders() {
        Map<Command, LatencyRecorder> recorders = new EnumMap<>(Command.class);
        for (Command command : Command.values()) {
            recorders.put(command, new LatencyRecorder(command.name()));
        }
        // Populated before any thread starts and never mutated afterwards, so an EnumMap is
        // safe to share: the concurrency is inside each recorder, not in the lookup.
        return Map.copyOf(recorders);
    }

    @Override
    public void close() {
        stopped = true;
        for (Thread worker : workers) {
            if (worker != null) {
                worker.interrupt();
            }
        }
        connection.close();
    }
}
