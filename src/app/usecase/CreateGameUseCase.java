package app.usecase;

import app.GameSession;
import app.SessionRegistry;
import app.model.SessionSummary;
import enums.GameMode;

/** Creates a game and registers it, without starting it. */
public final class CreateGameUseCase {

    private final SessionRegistry registry;

    public CreateGameUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param mode       CLASSIC or LUDO_T
     * @param seed       {@code null} for an unseeded game
     * @param tickMillis delay between automatic rounds
     */
    public SessionSummary execute(GameMode mode, Long seed, long tickMillis) {
        GameSession session = registry.create(mode, seed, tickMillis);
        return session.summary();
    }
}
