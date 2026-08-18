package app.port;

/** Kinds of notification the application layer emits about a session. */
public enum SessionEventType {

    /** A session came into existence. */
    CREATED,

    /** The session moved between {@code app.model.SessionState} values. */
    STATE_CHANGED,

    /** A round completed and a fresh snapshot is available. */
    SNAPSHOT,

    /** One human-readable line from the game's own event stream. */
    LOG,

    /** The game reached its end and the standings are final. */
    FINISHED
}
