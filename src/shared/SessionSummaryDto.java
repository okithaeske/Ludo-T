package shared;

import java.io.Serializable;

/** One row of the lobby table — enough to list a session without fetching its board. */
public record SessionSummaryDto(String gameId,
                                String mode,
                                Long seed,
                                String state,
                                int round,
                                int subscribers,
                                long tickMillis) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Seeded games are the reproducible ones; unseeded games show a dash in the lobby. */
    public boolean isSeeded() {
        return seed != null;
    }
}
