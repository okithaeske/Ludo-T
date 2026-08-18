package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

/**
 * Records that one more client is watching a game.
 *
 * <p>Separate from the connection's own subscription set on purpose: the connection tracks
 * <em>where</em> to send pushes, while this tracks <em>how many</em> viewers a session has, so
 * the lobby can show it. Both have to move together or the lobby reports nonsense.
 */
public final class SubscribeToGameUseCase {

    private final SessionRegistry registry;

    public SubscribeToGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public SessionSummary execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        session.addSubscriber();
        return session.summary();
    }
}
