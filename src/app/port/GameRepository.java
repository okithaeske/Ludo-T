package app.port;

import app.model.GameSnapshot;
import app.model.SessionSummary;

import java.util.List;

/**
 * <b>Output port.</b> Durable storage for finished games and their event streams.
 *
 * <p>Declared here, in the application layer, and implemented out in {@code persistence}
 * against H2 — the Dependency Inversion Principle applied across a process boundary. The
 * database tier is a detail this layer names but never sees.
 *
 * <p>Implementations must not block the caller: recording happens on the session thread, and
 * database latency must never become round latency. The JDBC implementation therefore queues
 * writes and returns immediately.
 *
 * <p>The in-memory {@code NO_OP} lets the server run with no database at all, which is how
 * steps 2–4 are exercised before the persistence tier exists.
 */
public interface GameRepository {

    /** Records that a session was created. */
    void recordSessionCreated(SessionSummary summary);

    /** Records one line from a game's event stream. */
    void recordEvent(String gameId, int round, String message);

    /** Records the final state of a finished game. */
    void recordResult(GameSnapshot finalSnapshot);

    /** Returns previously finished games, most recent first. */
    List<SessionSummary> findRecentResults(int limit);

    /** A repository that stores nothing, for running without the database tier. */
    GameRepository NO_OP = new GameRepository() {

        @Override
        public void recordSessionCreated(SessionSummary summary) {
            // Intentionally empty: no database tier attached.
        }

        @Override
        public void recordEvent(String gameId, int round, String message) {
            // Intentionally empty: no database tier attached.
        }

        @Override
        public void recordResult(GameSnapshot finalSnapshot) {
            // Intentionally empty: no database tier attached.
        }

        @Override
        public List<SessionSummary> findRecentResults(int limit) {
            return List.of();
        }
    };
}
