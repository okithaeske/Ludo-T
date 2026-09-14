package shared;

import java.io.Serializable;
import java.util.List;

/** One row of the History tab — a game that finished and now exists only in the database. */
public record FinishedGameDto(String gameId,
                              String mode,
                              int rounds,
                              String winnerColour,
                              String winnerStrategy,
                              List<String> finishingOrder,
                              long finishedAtMillis) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FinishedGameDto {
        // ArrayList, not List.copyOf: the immutable list type is not part of the wire
        // contract, and the whole record has to serialise. Same reasoning as
        // LobbyController.list().
        finishingOrder = finishingOrder == null
                ? List.of() : new java.util.ArrayList<>(finishingOrder);
    }

    /** True when the game ended with somebody home. */
    public boolean hasWinner() {
        return winnerColour != null && !winnerColour.isBlank();
    }
}
