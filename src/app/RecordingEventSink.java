package app;

import app.port.EventSink;
import app.port.GameRepository;
import app.port.SessionEvent;
import app.port.SessionEventType;

import java.util.function.IntSupplier;

/**
 * An {@link EventSink} that copies a game's log lines to the {@link GameRepository} on their
 * way to the real sink.
 *
 * <h2>Why a decorator rather than a call in {@code GameSession}</h2>
 * The narrative worth storing — who rolled what, who captured whom — is produced by
 * {@code GameEventBroadcaster} as the engine plays, and it reaches the outside world as
 * {@code LOG} events. {@code GameSession} never sees those lines; it only knows a round
 * finished. Recording from the session would therefore mean inventing a second, poorer
 * description of what happened, in a second place, that could drift from the one clients
 * actually see. Wrapping the sink stores exactly the stream the GUI shows.
 *
 * <p>It also keeps the engine and the broadcaster untouched: neither learns that a database
 * exists. The decorator is applied in {@link SessionRegistry}, which is the only class that
 * already knows about both.
 *
 * <h2>Threading</h2>
 * Invoked on the session's actor thread, like any sink, so it holds no locks and must not
 * block — which is exactly what the repository's write contract already promises. Only
 * {@link SessionEventType#LOG} events are recorded; snapshots are large, are superseded every
 * round, and are already stored once at the end as the game's result.
 */
public final class RecordingEventSink implements EventSink {

    private final EventSink delegate;
    private final GameRepository repository;
    private final String gameId;
    private final IntSupplier round;

    /**
     * @param round reads the engine's current round number at emit time. It is a supplier
     *              rather than a value because the listener — and therefore this sink — has to
     *              exist before the engine that will report the round does.
     */
    public RecordingEventSink(EventSink delegate, GameRepository repository, String gameId,
                              IntSupplier round) {
        this.delegate = delegate;
        this.repository = repository;
        this.gameId = gameId;
        this.round = round;
    }

    @Override
    public void publish(SessionEvent event) {
        if (event.type() == SessionEventType.LOG) {
            repository.recordEvent(gameId, round.getAsInt(), event.message());
        }
        delegate.publish(event);
    }
}
