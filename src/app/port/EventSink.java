package app.port;

/**
 * <b>Output port.</b> Where session notifications go.
 *
 * <p>The application layer depends on this interface; the network layer implements it. That
 * inversion is what keeps the dependency rule intact — {@code app} never mentions sockets, and
 * a test can substitute a sink that simply collects events in a list.
 *
 * <p>Implementations <b>must not block</b>. A sink is invoked from the session's own thread,
 * so a sink that waited on a slow client's socket would stall that game's round loop and, with
 * enough slow clients, the whole server. Implementations are expected to hand off to a queue
 * and return.
 */
@FunctionalInterface
public interface EventSink {

    /** Publishes one event. Must return promptly. */
    void publish(SessionEvent event);

    /** A sink that discards everything — the default for sessions nobody is watching. */
    EventSink NO_OP = event -> { };
}
