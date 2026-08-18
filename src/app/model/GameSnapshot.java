package app.model;

import java.util.List;

/**
 * A consistent picture of one game, produced by {@code GetSnapshotUseCase}.
 *
 * <p>Immutable and fully detached from the domain: it shares no object with the live
 * {@code Board}, so it stays valid however far the game advances afterwards. That detachment
 * is what makes it safe to hand to another thread and put on a socket.
 */
public record GameSnapshot(String gameId,
                           String mode,
                           SessionState state,
                           int round,
                           List<PlayerView> players,
                           int mysteryPosition,
                           int mysteryRoundsRemaining,
                           List<String> finishingOrder,
                           boolean gameOver) {

    public GameSnapshot {
        players = List.copyOf(players);
        finishingOrder = List.copyOf(finishingOrder);
    }
}
