package shared;

import java.io.Serializable;

/**
 * One piece as the client sees it. Pure data — the client never receives a domain
 * {@code model.Piece}, so no client can mutate server state by holding a reference.
 */
public record PieceDto(String id,
                       String colour,
                       String state,
                       int position,
                       int homeStraightPosition,
                       String direction,
                       String effect,
                       int effectRoundsLeft,
                       int captureCount) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** True when the piece sits in its colour's home straight rather than on the shared track. */
    public boolean inHomeStraight() {
        return homeStraightPosition > 0;
    }
}
