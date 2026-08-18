package shared;

/** Every request the server understands. */
public enum Command {

    /** Liveness check; carries no parameters. */
    PING,

    /** Creates a session. Params: {@code mode}, optional {@code seed}, optional {@code tickMillis}. */
    CREATE_GAME,

    /** Starts ticking a created session. Params: {@code gameId}. */
    START_GAME,

    /** Halts ticking without discarding state. Params: {@code gameId}. */
    PAUSE_GAME,

    /** Resumes a paused session. Params: {@code gameId}. */
    RESUME_GAME,

    /** Advances a paused or created session by exactly one round. Params: {@code gameId}. */
    STEP_ROUND,

    /** Ends a session permanently. Params: {@code gameId}. */
    ABORT_GAME,

    /** Returns a {@link SessionSummaryDto} for every live session. */
    LIST_GAMES,

    /** Returns the current {@link BoardSnapshot}. Params: {@code gameId}. */
    GET_SNAPSHOT,

    /** Registers this connection for pushes about a session. Params: {@code gameId}. */
    SUBSCRIBE,

    /** Stops pushes about a session. Params: {@code gameId}. */
    UNSUBSCRIBE,

    /** Changes round speed. Params: {@code gameId}, {@code tickMillis}. */
    SET_SPEED,

    /** Returns a {@link ServerMetricsDto} describing queue and connection load. */
    GET_METRICS
}
