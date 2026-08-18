package app.port;

/**
 * <b>Output port.</b> The application layer's only source of time.
 *
 * <p>Injected rather than called statically so that tests can drive elapsed time directly
 * instead of sleeping, which keeps the suite fast and free of timing flakiness.
 */
@FunctionalInterface
public interface Clock {

    /** Milliseconds since the epoch. */
    long millis();

    /** The real system clock. */
    Clock SYSTEM = System::currentTimeMillis;
}
