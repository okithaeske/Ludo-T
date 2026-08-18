package app;

import app.model.SessionState;
import app.model.SessionSummary;
import app.port.Clock;
import app.port.EventSink;
import app.port.GameRepository;
import app.port.SessionEvent;
import app.port.SessionListenerFactory;
import engine.GameEngine;
import engine.GameEngineBuilder;
import enums.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every game the server is hosting.
 *
 * <p>Backed by a {@link ConcurrentHashMap} rather than a synchronised map because the access
 * pattern is overwhelmingly read-heavy: every lobby listing, every routed command and every
 * broadcast looks a session up, while creation and removal are rare. Lookups therefore take no
 * lock at all, and writes lock only one bin, so listing games never blocks creating one.
 *
 * <p>One {@link ScheduledExecutorService} is shared by all sessions to pace their rounds. It
 * only ever hands work back to each session's own actor thread, so a slow round in one game
 * cannot delay another game's tick.
 */
public final class SessionRegistry {

    /** Sessions never tick faster than this, so a runaway game cannot monopolise a core. */
    private static final long MIN_TICK_MILLIS = 0L;

    private static final int SCHEDULER_THREADS = 2;

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();
    private final ScheduledExecutorService scheduler;
    private final EventSink sink;
    private final GameRepository repository;
    private final Clock clock;
    private final SessionListenerFactory listenerFactory;

    public SessionRegistry(EventSink sink, GameRepository repository, Clock clock,
                           SessionListenerFactory listenerFactory) {
        this.sink = sink;
        this.repository = repository;
        this.clock = clock;
        this.listenerFactory = listenerFactory;
        this.scheduler = Executors.newScheduledThreadPool(SCHEDULER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "session-tick");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Builds and registers a session. The game is created but not started — a client decides
     * when it runs.
     *
     * @param seed {@code null} for an unseeded game; any value makes the game reproducible
     */
    public GameSession create(GameMode mode, Long seed, long tickMillis) {
        String gameId = "g" + idSequence.incrementAndGet();

        GameEngineBuilder builder = new GameEngineBuilder()
                .withMode(mode)
                .withListener(listenerFactory.create(gameId, sink));
        if (seed != null) {
            builder.withSeed(seed);
        }
        GameEngine engine = builder.build();

        GameSession session = new GameSession(gameId, mode, seed,
                Math.max(MIN_TICK_MILLIS, tickMillis),
                engine, sink, repository, clock, scheduler);

        sessions.put(gameId, session);
        sink.publish(SessionEvent.created(gameId, session.summary()));
        return session;
    }

    public Optional<GameSession> find(String gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    /** All sessions, newest first. */
    public List<SessionSummary> list() {
        List<GameSession> live = new ArrayList<>(sessions.values());
        live.sort(Comparator.comparingLong(GameSession::getCreatedAtMillis).reversed());

        List<SessionSummary> summaries = new ArrayList<>(live.size());
        for (GameSession session : live) {
            summaries.add(session.summary());
        }
        return summaries;
    }

    public int liveCount() {
        return sessions.size();
    }

    /** Discards a session and releases its thread. */
    public void remove(String gameId) {
        GameSession removed = sessions.remove(gameId);
        if (removed != null) {
            removed.shutdown();
        }
    }

    /** Drops every session that has finished or been aborted, freeing their threads. */
    public int purgeTerminated() {
        int purged = 0;
        for (GameSession session : new ArrayList<>(sessions.values())) {
            if (session.getState().isTerminal()) {
                remove(session.getGameId());
                purged++;
            }
        }
        return purged;
    }

    /** Stops every session and the shared scheduler. */
    public void shutdown() {
        for (String gameId : new ArrayList<>(sessions.keySet())) {
            remove(gameId);
        }
        scheduler.shutdownNow();
    }

    /** Exposed for tests that need to assert on a session's state directly. */
    public SessionState stateOf(String gameId) {
        return find(gameId).map(GameSession::getState).orElse(null);
    }
}
