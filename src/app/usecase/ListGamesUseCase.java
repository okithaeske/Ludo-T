package app.usecase;

import app.SessionRegistry;
import app.model.SessionSummary;

import java.util.List;

/** Lists every session the server is hosting, newest first. */
public final class ListGamesUseCase {

    private final SessionRegistry registry;

    public ListGamesUseCase(SessionRegistry registry) {
        this.registry = registry;
    }

    public List<SessionSummary> execute() {
        return registry.list();
    }
}
