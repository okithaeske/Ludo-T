package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Advances a created or paused game by exactly one round, then stops again. */
public final class StepRoundUseCase {

    private final SessionRegistry registry;

    public StepRoundUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.step().thenApply(ignored -> session.summary());
    }
}
