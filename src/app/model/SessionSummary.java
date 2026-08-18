package app.model;

/** Lightweight description of a session for lobby listings. {@code seed} is null when unseeded. */
public record SessionSummary(String gameId,
                             String mode,
                             Long seed,
                             SessionState state,
                             int round,
                             int subscribers,
                             long tickMillis) {
}
