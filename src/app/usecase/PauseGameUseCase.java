package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Halts round ticking, leaving the board exactly where it stands. */
public final class PauseGameUseCase {

    private final SessionRegistry registry;

    public PauseGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.pause().thenApply(ignored -> session.summary());
    }
}
