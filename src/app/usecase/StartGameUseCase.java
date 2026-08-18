package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Begins automatic round ticking for a created or paused game. */
public final class StartGameUseCase {

    private final SessionRegistry registry;

    public StartGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.start().thenApply(ignored -> session.summary());
    }
}
