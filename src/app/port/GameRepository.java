package app.port;

import app.model.FinishedGame;
import app.model.GameSnapshot;
import app.model.SessionSummary;
import app.model.StrategyRanking;

import java.util.List;

/**
 * <b>Output port.</b> Durable storage for finished games and their event streams.
 *
 * <p>Declared here, in the application layer, and implemented out in {@code persistence}
 * against H2 — the Dependency Inversion Principle applied across a process boundary. The
 * database tier is a detail this layer names but never sees.
 *
 * <h2>The write contract: never block the caller</h2>
 * The three {@code record*} methods are called from a session's own actor thread, in the
 * middle of playing a round. If one of them waited for a network round trip to the database
 * process, database latency would become <em>round</em> latency, and a slow or stopped
 * database would stall every game that touched it. So they are defined as
 * <em>fire-and-forget</em>: an implementation must hand the work to something else and return
 * immediately, and must prefer losing a row to delaying a game.
 *
 * <p>That is a deliberate trade. This tier stores the metagame — history and leaderboards —
 * where a missing row costs a line in a table nobody is reading yet. It would be the wrong
 * trade for the simulation itself, which is why the simulation is not stored here at all: a
 * live game exists only in its actor's memory.
 *
 * <h2>The read contract: blocking is fine</h2>
 * The two {@code find*} methods are the opposite. They are called from a request-worker
 * thread, which exists precisely so that slow work does not touch a game, and their caller is
 * a client waiting for an answer. They may block, and they return the current truth rather
 * than a promise.
 *
 * <p>The in-memory {@code NO_OP} lets the server run with no database at all — how steps 2–4
 * were exercised before the persistence tier existed, and still the fallback when the database
 * process is not up.
 */
public interface GameRepository {

    /** Records that a session began. Must not block. */
    void recordSessionCreated(SessionSummary summary);

    /** Records one line from a game's event stream. Must not block. */
    void recordEvent(String gameId, int round, String message);

    /** Records the final state of a finished game. Must not block. */
    void recordResult(GameSnapshot finalSnapshot);

    /** Finished games, most recently finished first. May block. */
    List<FinishedGame> findRecentResults(int limit);

    /** How each AI strategy has performed across every finished game. May block. */
    List<StrategyRanking> findStrategyLeaderboard();

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
        public List<FinishedGame> findRecentResults(int limit) {
            return List.of();
        }

        @Override
        public List<StrategyRanking> findStrategyLeaderboard() {
            return List.of();
        }
    };
}
