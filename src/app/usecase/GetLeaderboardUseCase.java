package app.usecase;

import app.model.StrategyRanking;
import app.port.GameRepository;

import java.util.List;

/**
 * Reads how each AI strategy has performed across every game the server has ever finished.
 *
 * <p>This is the use case that justifies the database tier existing at all. Every other answer
 * in the system can be produced from memory, because it concerns a game that is currently
 * hosted. This one cannot: it is an aggregate over games whose sessions were shut down and
 * whose actors no longer exist. Persistence is not decoration here — it is the only way the
 * question can be asked.
 *
 * <p>The aggregation itself is done by the {@code strategy_leaderboard} view in the database,
 * not here. See {@link StrategyRanking}.
 */
public final class GetLeaderboardUseCase {

    private final GameRepository repository;

    public GetLeaderboardUseCase(GameRepository repository) {
        this.repository = repository;
    }

    public List<StrategyRanking> execute() {
        return repository.findStrategyLeaderboard();
    }
}
