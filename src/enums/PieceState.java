package enums;

/**
 * <b>State pattern</b> — the three life-cycle states of a {@link model.Piece}.
 * Engine and rule code branch on this value to change behaviour:
 * <ul>
 *   <li>{@code BASE} — piece is off-board; can only leave on a roll of
 *       {@link model.GameConstants#MAX_DICE_ROLL}.</li>
 *   <li>{@code ACTIVE} — piece is on the standard board or home straight and moves normally.</li>
 *   <li>{@code HOME} — piece has finished; excluded from all further play and counted
 *       by {@link engine.WinTracker}.</li>
 * </ul>
 *
 * @see model.Piece#getState()
 * @see model.Piece#setState(PieceState)
 * @see engine.RuleEngine#validateMove(model.Piece, int)
 */
public enum PieceState {
    BASE,
    ACTIVE,
    HOME
}
