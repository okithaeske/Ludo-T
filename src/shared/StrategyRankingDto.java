package shared;

import java.io.Serializable;

/** One row of the Leaderboard tab, aggregated by the database's {@code strategy_leaderboard}. */
public record StrategyRankingDto(String strategy,
                                 int gamesPlayed,
                                 int wins,
                                 int totalCaptures,
                                 double averagePiecesHome) implements Serializable {

    private static final long serialVersionUID = 1L;

    public double winRate() {
        return gamesPlayed == 0 ? 0.0 : (double) wins / gamesPlayed;
    }
}
