package app.port;

import app.model.GameSnapshot;
import app.model.SessionSummary;

/**
 * Something worth telling interested parties about. Framework-free: it names no transport, so
 * the application layer cannot tell whether a listener writes to a socket, a log file, or a
 * test's list.
 *
 * <p>{@code snapshot} and {@code summary} are populated only for the event types that carry
 * them; {@code message} only for {@link SessionEventType#LOG}.
 */
public record SessionEvent(SessionEventType type,
                           String gameId,
                           String message,
                           GameSnapshot snapshot,
                           SessionSummary summary) {

    public static SessionEvent log(String gameId, String message) {
        return new SessionEvent(SessionEventType.LOG, gameId, message, null, null);
    }

    public static SessionEvent snapshot(String gameId, GameSnapshot snapshot) {
        return new SessionEvent(SessionEventType.SNAPSHOT, gameId, null, snapshot, null);
    }

    public static SessionEvent finished(String gameId, GameSnapshot snapshot) {
        return new SessionEvent(SessionEventType.FINISHED, gameId, null, snapshot, null);
    }

    public static SessionEvent created(String gameId, SessionSummary summary) {
        return new SessionEvent(SessionEventType.CREATED, gameId, null, null, summary);
    }

    public static SessionEvent stateChanged(String gameId, SessionSummary summary) {
        return new SessionEvent(SessionEventType.STATE_CHANGED, gameId, null, null, summary);
    }
}
