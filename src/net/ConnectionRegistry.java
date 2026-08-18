package net;

import adapter.MetricsProvider;
import adapter.ServerEventMapper;
import app.port.Clock;
import app.port.EventSink;
import app.port.SessionEvent;
import shared.ServerEvent;
import shared.ServerEventType;
import shared.ServerMetricsDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Every open connection, and the fan-out point for server pushes.
 *
 * <p>Implements {@link EventSink}, which is the port the application layer publishes to. This
 * is where the dependency rule pays off: {@code app} knows only that events go "to a sink",
 * and the fact that a sink means sockets lives out here, in the outermost layer.
 *
 * <p>Publishing never blocks. Each connection is handed the event and returns at once, because
 * a connection only queues it for its own writer thread. That matters because
 * {@link #publish(SessionEvent)} runs on a <em>session</em> thread: if fan-out blocked on a
 * slow client's socket, that game's rounds would stall for every viewer, and one unresponsive
 * client could freeze a game for everyone watching it.
 */
public final class ConnectionRegistry implements EventSink, MetricsProvider {

    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Clock clock;
    private final RequestQueue requestQueue;

    /**
     * Set after construction to break a wiring cycle: sessions publish to this registry, so
     * the registry must exist first, yet its metrics report how many sessions are live.
     */
    private volatile IntSupplier liveSessionCount = () -> 0;

    public ConnectionRegistry(Clock clock, RequestQueue requestQueue) {
        this.clock = clock;
        this.requestQueue = requestQueue;
    }

    /**
     * Called for each game a closing connection was watching, so viewer counts fall back when a
     * client vanishes. Without it a crashed client would inflate a game's subscriber count for
     * the rest of the server's life.
     */
    private volatile java.util.function.Consumer<String> subscriptionReleaser = gameId -> { };

    public void setLiveSessionCount(IntSupplier liveSessionCount) {
        this.liveSessionCount = liveSessionCount;
    }

    public void setSubscriptionReleaser(java.util.function.Consumer<String> releaser) {
        this.subscriptionReleaser = releaser;
    }

    /** Releases a closing connection's subscriptions. Never throws. */
    void releaseSubscriptions(Collection<String> gameIds) {
        for (String gameId : gameIds) {
            try {
                subscriptionReleaser.accept(gameId);
            } catch (RuntimeException ignored) {
                // The game may already be gone; the connection is closing regardless.
            }
        }
    }

    void add(ClientConnection connection) {
        connections.put(connection.id(), connection);
    }

    void remove(String connectionId) {
        connections.remove(connectionId);
    }

    public int connectedCount() {
        return connections.size();
    }

    @Override
    public void publish(SessionEvent event) {
        boolean lobbyWide = ServerEventMapper.isLobbyWide(event);
        long now = clock.millis();

        for (ClientConnection connection : connections.values()) {
            if (!connection.isOpen()) {
                continue;
            }
            if (lobbyWide || connection.isSubscribed(event.gameId())) {
                // Sequence numbers are per connection, so each client sees an unbroken run.
                connection.pushWithSequence(
                        sequence -> ServerEventMapper.toServerEvent(event, sequence, now));
            }
        }
    }

    /**
     * Pushes the current load figures to every connected client.
     *
     * <p>Server-wide rather than per-game, so it goes to everyone regardless of subscriptions:
     * the point is that a tutor watching any client can see the queue fill and drain while the
     * test clients fire.
     */
    public void broadcastMetrics() {
        ServerMetricsDto metrics = currentMetrics();
        long now = clock.millis();
        for (ClientConnection connection : connections.values()) {
            if (connection.isOpen()) {
                connection.pushWithSequence(sequence -> new ServerEvent(
                        ServerEventType.METRICS, null, metrics, sequence, now));
            }
        }
    }

    @Override
    public ServerMetricsDto currentMetrics() {
        return new ServerMetricsDto(
                requestQueue.depth(),
                requestQueue.capacity(),
                requestQueue.activeWorkers(),
                requestQueue.poolSize(),
                requestQueue.acceptedCount(),
                requestQueue.completedCount(),
                requestQueue.saturationCount(),
                connections.size(),
                liveSessionCount.getAsInt());
    }

    /** Closes every connection. */
    public void closeAll() {
        Collection<ClientConnection> open = new ArrayList<>(connections.values());
        for (ClientConnection connection : open) {
            connection.close();
        }
        connections.clear();
    }
}
