package adapter;

import app.port.SessionEvent;
import shared.ServerEvent;
import shared.ServerEventType;

/** Translates application session events into the push messages clients receive. */
public final class ServerEventMapper {

    private ServerEventMapper() {
        // Static mapper; not instantiable.
    }

    /**
     * @param sequence per-connection, strictly increasing, so a client can spot a gap
     * @param nowMillis timestamp to stamp on the event
     */
    public static ServerEvent toServerEvent(SessionEvent event, long sequence, long nowMillis) {
        return switch (event.type()) {
            case LOG -> new ServerEvent(ServerEventType.LOG, event.gameId(),
                    event.message(), sequence, nowMillis);
            case SNAPSHOT -> new ServerEvent(ServerEventType.SNAPSHOT, event.gameId(),
                    SnapshotMapper.toDto(event.snapshot()), sequence, nowMillis);
            case FINISHED -> new ServerEvent(ServerEventType.GAME_OVER, event.gameId(),
                    SnapshotMapper.toDto(event.snapshot()), sequence, nowMillis);
            case CREATED -> new ServerEvent(ServerEventType.GAME_CREATED, event.gameId(),
                    SnapshotMapper.toDto(event.summary()), sequence, nowMillis);
            case STATE_CHANGED -> new ServerEvent(ServerEventType.GAME_STATE_CHANGED, event.gameId(),
                    SnapshotMapper.toDto(event.summary()), sequence, nowMillis);
        };
    }

    /**
     * True when every client should see this event, not only those watching the game.
     *
     * <p>Lobby-level news — a game appearing, a game changing state — must reach clients that
     * have not subscribed to that game, otherwise a new session would never show up in anyone
     * else's lobby table. Per-round traffic stays restricted to subscribers so that watching
     * one game does not mean receiving every board on the server.
     */
    public static boolean isLobbyWide(SessionEvent event) {
        return switch (event.type()) {
            case CREATED, STATE_CHANGED -> true;
            case LOG, SNAPSHOT, FINISHED -> false;
        };
    }
}
