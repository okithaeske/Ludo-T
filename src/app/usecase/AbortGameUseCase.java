package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Ends a game permanently. The session stays listed so clients can see it was aborted. */
public final class AbortGameUseCase {

    private final SessionRegistry registry;

    public AbortGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.abort().thenApply(ignored -> session.summary());
    }
}
