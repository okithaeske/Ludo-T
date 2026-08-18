package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.GameSnapshot;

import java.util.concurrent.CompletableFuture;

/**
 * Returns a consistent picture of one game.
 *
 * <p>The work happens on the session's own thread, so the snapshot never catches the board
 * mid-round — see {@link app.SnapshotFactory}.
 */
public final class GetSnapshotUseCase {

    private final SessionRegistry registry;

    public GetSnapshotUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<GameSnapshot> execute(String gameId) {
        GameSession session = registry.find(gameId)
                .orElseThrow(() -> new SessionNotFoundException(gameId));
        return session.snapshot();
    }
}
