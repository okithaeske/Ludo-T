package app.usecase;

import app.model.FinishedGame;
import app.port.GameRepository;

import java.util.List;

/**
 * Reads finished games back out of the database tier.
 *
 * <p>The counterpart to {@link ListGamesUseCase}: that one answers "what is the server hosting
 * right now" from the {@code SessionRegistry} in memory, this one answers "what has ever
 * finished" from storage. Two use cases rather than one because they have different sources,
 * different lifetimes and different failure modes — a live listing cannot fail, and a
 * historical one can come back empty because the database process is down.
 *
 * <p>Runs on a request-worker thread and is allowed to block; see the read contract on
 * {@link GameRepository}.
 */
public final class GetHistoryUseCase {

    /** Enough to fill the History tab without paging, and small enough to serialise cheaply. */
    public static final int DEFAULT_LIMIT = 50;

    private final GameRepository repository;

    public GetHistoryUseCase(GameRepository repository) {
        this.repository = repository;
    }

    public List<FinishedGame> execute(int limit) {
        return repository.findRecentResults(limit <= 0 ? DEFAULT_LIMIT : limit);
    }
}
