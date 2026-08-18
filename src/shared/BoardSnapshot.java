package shared;

import java.io.Serializable;
import java.util.List;

/**
 * A complete, immutable picture of one game at one instant — everything a client needs to
 * paint the board without asking further questions.
 *
 * <p>Built inside the session's own thread, so it is always internally consistent: it can
 * never show a piece half-way through a move, or a round number from before a capture that
 * it also shows.
 */
public record BoardSnapshot(String gameId,
                            String mode,
                            String state,
                            int round,
                            List<PlayerDto> players,
                            int mysteryPosition,
                            int mysteryRoundsRemaining,
                            List<String> finishingOrder,
                            boolean gameOver) implements Serializable {

    private static final long serialVersionUID = 1L;

    public BoardSnapshot {
        players = List.copyOf(players);
        finishingOrder = List.copyOf(finishingOrder);
    }

    public boolean hasMysteryCell() {
        return mysteryPosition >= 0;
    }
}
