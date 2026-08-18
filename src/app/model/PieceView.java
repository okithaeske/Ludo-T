package app.model;

/**
 * Application-layer output model for one piece: a flat, immutable copy taken from a domain
 * {@code model.Piece} while the session thread owns it.
 */
public record PieceView(String id,
                        String colour,
                        String state,
                        int position,
                        int homeStraightPosition,
                        String direction,
                        String effect,
                        int effectRoundsLeft,
                        int captureCount) {
}
