package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Resumes a paused game from the round it stopped on. */
public final class ResumeGameUseCase {

    private final SessionRegistry registry;

    public ResumeGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.resume().thenApply(ignored -> session.summary());
    }
}
