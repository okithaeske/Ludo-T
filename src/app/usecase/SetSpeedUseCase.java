package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.concurrent.CompletableFuture;

/** Changes how long a game waits between rounds. Takes effect from the next round. */
public final class SetSpeedUseCase {

    private final SessionRegistry registry;

    public SetSpeedUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SessionSummary> execute(String gameId, long tickMillis) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.setTickMillis(tickMillis).thenApply(ignored -> session.summary());
    }
}
