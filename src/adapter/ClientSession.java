package adapter;

/**
 * What a controller is allowed to know about the client that sent a request.
 *
 * <p>Declared here and implemented by the network layer, so controllers can register a client
 * for pushes without ever seeing a {@code Socket}. A test can drive the router with a plain
 * in-memory implementation.
 */
public interface ClientSession {

    /** Stable identifier for this connection, used in logs and metrics. */
    String id();

    /** Registers this client for pushes about {@code gameId}. Idempotent. */
    void subscribe(String gameId);

    /** Stops pushes about {@code gameId}. Idempotent. */
    void unsubscribe(String gameId);

    /** True when this client currently receives pushes for {@code gameId}. */
    boolean isSubscribed(String gameId);
}
