package shared;

/** Kinds of unsolicited message the server pushes to subscribed clients. */
public enum ServerEventType {

    /** A session was created by some client. Payload: {@link SessionSummaryDto}. */
    GAME_CREATED,

    /** A session changed state (started, paused, resumed, aborted, finished).
     *  Payload: {@link SessionSummaryDto}. */
    GAME_STATE_CHANGED,

    /** A round completed. Payload: {@link BoardSnapshot}. */
    SNAPSHOT,

    /** One human-readable line from the game's event stream. Payload: {@code String}. */
    LOG,

    /** The game ended. Payload: {@link BoardSnapshot} with the final standings. */
    GAME_OVER,

    /** Periodic load report. Payload: {@link ServerMetricsDto}. */
    METRICS
}
