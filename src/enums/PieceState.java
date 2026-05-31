package enums;

/**
 * <b>State pattern</b> — the three life-cycle states of a {@link model.Piece}.
 * Each constant overrides abstract behavioural methods so callers can query
 * behaviour directly ({@code piece.getState().hasFinished()}) instead of
 * scattering {@code == PieceState.HOME} comparisons across the codebase.
 *
 * <ul>
 *   <li>{@code BASE} — piece is off-board; requires a roll of
 *       {@link model.GameConstants#MAX_DICE_ROLL} to enter play.</li>
 *   <li>{@code ACTIVE} — piece is on the standard board or home straight.</li>
 *   <li>{@code HOME} — piece has finished; excluded from all further play.</li>
 * </ul>
 *
 * @see model.Piece#getState()
 * @see model.Piece#setState(PieceState)
 * @see engine.RuleEngine#validateMove(model.Piece, int)
 */
public enum PieceState {

    BASE {
        @Override public boolean isOnBoard()         { return false; }
        @Override public boolean hasFinished()       { return false; }
        @Override public boolean requiresSixToMove() { return true;  }
    },
    ACTIVE {
        @Override public boolean isOnBoard()         { return true;  }
        @Override public boolean hasFinished()       { return false; }
        @Override public boolean requiresSixToMove() { return false; }
    },
    HOME {
        @Override public boolean isOnBoard()         { return false; }
        @Override public boolean hasFinished()       { return true;  }
        @Override public boolean requiresSixToMove() { return false; }
    };

    /** True only when the piece occupies a standard-board or home-straight cell. */
    public abstract boolean isOnBoard();

    /** True only when the piece has reached the home square. */
    public abstract boolean hasFinished();

    /** True only when the piece needs a 6 before it can move at all. */
    public abstract boolean requiresSixToMove();
}
