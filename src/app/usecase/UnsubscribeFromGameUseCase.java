package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

/** Records that one fewer client is watching a game. */
public final class UnsubscribeFromGameUseCase {

    private final SessionRegistry registry;

    public UnsubscribeFromGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public SessionSummary execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        session.removeSubscriber();
        return session.summary();
    }
}
