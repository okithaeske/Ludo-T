package shared;

import java.io.Serializable;

/**
 * A message the server sends without being asked.
 *
 * <p>This is the mechanism behind "when one client changes data displayed on another client,
 * the other client shows the change immediately": clients do not poll. Client A pauses a
 * game, the server pushes {@link ServerEventType#GAME_STATE_CHANGED} to everyone subscribed,
 * and client B's board freezes on arrival.
 *
 * <p>{@code sequence} is per-connection and strictly increasing, so a client can detect a
 * dropped or reordered push rather than silently rendering stale state.
 */
public final class ServerEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ServerEventType type;
    private final String gameId;
    private final Serializable payload;
    private final long sequence;
    private final long timestampMillis;

    public ServerEvent(ServerEventType type, String gameId, Serializable payload,
                       long sequence, long timestampMillis) {
        this.type = type;
        this.gameId = gameId;
        this.payload = payload;
        this.sequence = sequence;
        this.timestampMillis = timestampMillis;
    }

    public ServerEventType getType() {
        return type;
    }

    /** The session this event concerns, or {@code null} for server-wide events such as metrics. */
    public String getGameId() {
        return gameId;
    }

    public Serializable getPayload() {
        return payload;
    }

    public long getSequence() {
        return sequence;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public String toString() {
        return "ServerEvent[" + sequence + "] " + type
                + (gameId == null ? "" : " game=" + gameId)
                + (payload == null ? "" : " " + payload);
    }
}
