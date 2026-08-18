package app;

import app.model.GameSnapshot;
import app.model.SessionState;
import app.model.SessionSummary;
import app.port.Clock;
import app.port.EventSink;
import app.port.GameRepository;
import app.port.SessionEvent;
import engine.GameEngine;
import enums.GameMode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One hosted game, run as an <b>actor</b>: every operation that touches the engine is
 * submitted to a single-threaded executor owned by this session.
 *
 * <h2>Why an actor rather than locks</h2>
 * A {@code GameEngine} is a deep graph of mutable objects — board, pieces, players, turn
 * manager. Guarding it with {@code synchronized} would mean either one coarse lock (correct
 * but it serialises unrelated games too) or many fine locks (fast, and a standing invitation
 * to deadlock as an operation walks from board to piece to player). Confining the graph to one
 * thread removes the question entirely: there is no shared access to guard, so the engine
 * needs no locks and no {@code synchronized} anywhere, and it stays exactly the
 * single-threaded code Assignment 1 tested.
 *
 * <p>Crucially this does not serialise the <em>server</em>: each session owns a separate
 * thread, so twenty games genuinely advance in parallel. Only operations on the <em>same</em>
 * game queue behind one another, which is precisely the ordering a game needs anyway — a pause
 * arriving mid-round takes effect at the round boundary rather than tearing the board in half.
 *
 * <p>That thread is a <b>virtual</b> thread — see {@link #actorThreadFactory(String)}. The
 * confinement argument above is unchanged by that choice; only the cost per game is.
 *
 * <h2>Ticking</h2>
 * Rounds are not run in a loop with a sleep, which would hold the actor thread and make the
 * session deaf to pause and abort. Instead each completed round schedules the next one on a
 * shared {@link ScheduledExecutorService}; between rounds the actor is idle and free to serve
 * commands. Self-scheduling also means a round that overruns its tick interval delays the next
 * round rather than overlapping with it.
 */
public final class GameSession {

    private final String gameId;
    private final GameMode mode;
    private final Long seed;
    private final GameEngine engine;
    private final EventSink sink;
    private final GameRepository repository;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService actor;
    private final AtomicInteger subscribers = new AtomicInteger();
    private final long createdAtMillis;

    /** Read from any thread for cheap status queries; written only on the actor thread. */
    private volatile SessionState state = SessionState.CREATED;

    /** Read by the scheduler, written on the actor thread. */
    private volatile long tickMillis;

    /** Actor-confined: only ever touched inside a task submitted to {@link #actor}. */
    private ScheduledFuture<?> pendingTick;

    /** Actor-confined: guards against announcing the game twice. */
    private boolean begun;

    GameSession(String gameId, GameMode mode, Long seed, long tickMillis, GameEngine engine,
                EventSink sink, GameRepository repository, Clock clock,
                ScheduledExecutorService scheduler) {
        this.gameId = gameId;
        this.mode = mode;
        this.seed = seed;
        this.tickMillis = tickMillis;
        this.engine = engine;
        this.sink = sink;
        this.repository = repository;
        this.clock = clock;
        this.scheduler = scheduler;
        this.createdAtMillis = clock.millis();
        this.actor = Executors.newSingleThreadExecutor(actorThreadFactory(gameId));
    }

    /**
     * A <b>virtual</b> thread per game.
     *
     * <p>Confinement is what makes the engine safe, and a single-threaded executor still
     * provides it — one task at a time, no shared access to guard. Only the price changes: a
     * platform thread reserves ~1 MB of stack whether or not it is doing anything, and a game
     * actor is idle between ticks, which is almost always. A virtual thread costs a few hundred
     * bytes of heap and releases its carrier the instant it parks, so the thread ceiling stops
     * being the limit on how many games the server can host.
     *
     * <p>Naming survives the change, so a stack trace still identifies the game. Virtual
     * threads are always daemons, so a forgotten session cannot keep the JVM alive either.
     */
    private static ThreadFactory actorThreadFactory(String gameId) {
        return Thread.ofVirtual().name("game-" + gameId).factory();
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** Starts (or restarts, after a pause) automatic round ticking. */
    public CompletableFuture<Void> start() {
        return submit(() -> {
            if (state.isTerminal() || state == SessionState.RUNNING) {
                return;
            }
            ensureBegun();
            transitionTo(SessionState.RUNNING);
            scheduleNextTick();
        });
    }

    /** Stops ticking without discarding the game. */
    public CompletableFuture<Void> pause() {
        return submit(() -> {
            if (state != SessionState.RUNNING) {
                return;
            }
            cancelPendingTick();
            transitionTo(SessionState.PAUSED);
        });
    }

    /** Resumes a paused game. Equivalent to {@link #start()} but rejects a fresh session. */
    public CompletableFuture<Void> resume() {
        return submit(() -> {
            if (state != SessionState.PAUSED) {
                return;
            }
            transitionTo(SessionState.RUNNING);
            scheduleNextTick();
        });
    }

    /** Advances exactly one round. Only meaningful while created or paused. */
    public CompletableFuture<Void> step() {
        return submit(() -> {
            if (!state.canStep()) {
                return;
            }
            ensureBegun();
            advanceOneRound();
        });
    }

    /** Ends the game permanently and releases its thread. */
    public CompletableFuture<Void> abort() {
        return submit(() -> {
            if (state.isTerminal()) {
                return;
            }
            cancelPendingTick();
            transitionTo(SessionState.ABORTED);
        }).whenComplete((ignored, error) -> actor.shutdown());
    }

    /** Changes the delay between rounds. Takes effect from the next scheduled round. */
    public CompletableFuture<Void> setTickMillis(long millis) {
        return submit(() -> tickMillis = Math.max(0L, millis));
    }

    /**
     * Photographs the game.
     *
     * <p>Runs on the actor thread, so the picture is always consistent — it can never catch
     * the board mid-round.
     */
    public CompletableFuture<GameSnapshot> snapshot() {
        return supply(() -> SnapshotFactory.capture(gameId, engine, state));
    }

    // ── Queries (safe from any thread) ───────────────────────────────────────

    public String getGameId() {
        return gameId;
    }

    public SessionState getState() {
        return state;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public int addSubscriber() {
        return subscribers.incrementAndGet();
    }

    public int removeSubscriber() {
        return subscribers.updateAndGet(current -> Math.max(0, current - 1));
    }

    /**
     * A cheap status read that does not queue behind the actor.
     *
     * <p>The round number is read without synchronisation and may lag by one round under load.
     * That is deliberate: lobby listings are read constantly and a stale round counter is
     * harmless, whereas making every listing wait for its turn on each game's actor would let
     * one slow game delay the whole lobby.
     */
    public SessionSummary summary() {
        return new SessionSummary(gameId, mode.name(), seed, state,
                engine.getRoundNumber(), subscribers.get(), tickMillis);
    }

    /** Stops ticking and releases the session's thread. */
    public void shutdown() {
        cancelPendingTick();
        actor.shutdownNow();
    }

    // ── Actor-thread internals ───────────────────────────────────────────────

    private void ensureBegun() {
        if (begun) {
            return;
        }
        begun = true;
        engine.beginGame();
        repository.recordSessionCreated(summary());
    }

    private void scheduleNextTick() {
        if (state != SessionState.RUNNING) {
            return;
        }
        try {
            pendingTick = scheduler.schedule(
                    () -> submit(this::runTick), tickMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Server is shutting down; stop ticking rather than failing the session.
            transitionTo(SessionState.ABORTED);
        }
    }

    private void runTick() {
        if (state != SessionState.RUNNING) {
            return;
        }
        advanceOneRound();
        scheduleNextTick();
    }

    private void advanceOneRound() {
        if (engine.isGameOver()) {
            finish();
            return;
        }

        engine.executeRound();

        GameSnapshot snapshot = SnapshotFactory.capture(gameId, engine, state);
        sink.publish(SessionEvent.snapshot(gameId, snapshot));

        if (engine.isGameOver()) {
            finish();
        }
    }

    private void finish() {
        cancelPendingTick();
        state = SessionState.FINISHED;
        GameSnapshot finalSnapshot = SnapshotFactory.capture(gameId, engine, state);
        repository.recordResult(finalSnapshot);
        sink.publish(SessionEvent.finished(gameId, finalSnapshot));
        sink.publish(SessionEvent.stateChanged(gameId, summary()));
    }

    private void transitionTo(SessionState next) {
        state = next;
        sink.publish(SessionEvent.stateChanged(gameId, summary()));
    }

    private void cancelPendingTick() {
        if (pendingTick != null) {
            pendingTick.cancel(false);
            pendingTick = null;
        }
    }

    // ── Submission helpers ───────────────────────────────────────────────────

    private CompletableFuture<Void> submit(Runnable task) {
        return supply(() -> {
            task.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> supply(java.util.function.Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            actor.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (RuntimeException e) {
                    // One bad round must not kill the session's thread and silently freeze
                    // the game; report it to the caller and leave the actor usable.
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(
                    new IllegalStateException("Session " + gameId + " is no longer running", e));
        }
        return future;
    }
}
