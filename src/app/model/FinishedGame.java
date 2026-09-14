package app.model;

import java.util.List;

/**
 * Application-layer output model for one game read back out of the database tier.
 *
 * <p>Distinct from {@link SessionSummary}, which describes a game that is still hosted in
 * memory. This one describes a game that is over and now exists only as rows: it carries the
 * outcome ({@code winnerColour}, {@code finishingOrder}) that a live summary has no reason to
 * hold, and none of the liveness ({@code subscribers}, {@code tickMillis}) that a finished
 * game no longer has. Reusing one record for both would leave half its fields meaningless in
 * each direction.
 *
 * @param finishedAtMillis epoch milliseconds, so the application layer never has to agree with
 *                         the database on a time zone; the client formats it for display
 * @param winnerColour     null when the game ended with nobody home
 */
public record FinishedGame(String gameId,
                           String mode,
                           int rounds,
                           String winnerColour,
                           String winnerStrategy,
                           List<String> finishingOrder,
                           long finishedAtMillis) {

    public FinishedGame {
        finishingOrder = List.copyOf(finishingOrder);
    }
}
