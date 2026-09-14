package app.model;

/**
 * Application-layer output model for one row of the strategy leaderboard.
 *
 * <p>The aggregation behind this record is done by the database, not by Java — see the
 * {@code strategy_leaderboard} view in {@code db/schema.sql}. Counting wins in a loop here
 * would mean shipping every player row of every game across the wire to add them up, and would
 * put the database tier's job in the application layer.
 *
 * <p>This is the one answer in the system that no single game can produce: it exists only
 * because results outlive the sessions that made them, which is the argument for having a
 * database tier at all.
 */
public record StrategyRanking(String strategy,
                              int gamesPlayed,
                              int wins,
                              int totalCaptures,
                              double averagePiecesHome) {

    /** Wins as a fraction of games played; 0 when the strategy has never played. */
    public double winRate() {
        return gamesPlayed == 0 ? 0.0 : (double) wins / gamesPlayed;
    }
}
