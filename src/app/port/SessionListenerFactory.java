package app.port;

import logger.GameEventListener;

/**
 * <b>Output port.</b> Supplies the observer that a new session attaches to its engine.
 *
 * <p>This is the seam that makes the whole refactor cheap. {@code logger.GameEventListener}
 * was already the domain's output port for events, with {@code Logger} as its only
 * implementation. The server passes a factory producing a broadcaster instead, and the engine
 * publishes to it exactly as it published to {@code Logger} — with no idea its events now
 * leave the process. Not one engine class changed to make that happen.
 */
@FunctionalInterface
public interface SessionListenerFactory {

    /** Creates the listener for one session. */
    GameEventListener create(String gameId, EventSink sink);
}
