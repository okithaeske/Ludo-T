/**
 * Players and piece ownership.
 *
 * <p><b>Start here:</b> {@link player.AbstractPlayer} defines the shared interface for all
 * four colour players. {@link player.PlayerFactory} creates them (Factory pattern) and wires
 * each one with its assigned {@link player.strategy.PieceSelectionStrategy}.
 *
 * <h2>Class responsibilities</h2>
 * <ul>
 *   <li>{@link player.AbstractPlayer} &ndash; Owns the four {@link model.Piece} objects,
 *       provides board-query helpers ({@code getPiecesOnBoard}, {@code getPieceClosestToHome},
 *       {@code canCaptureOpponent}), and delegates piece selection to its strategy.</li>
 *   <li>{@link player.PlayerFactory} &ndash; <em>Factory.</em> Instantiates all four
 *       concrete players and assigns their strategies.</li>
 *   <li>{@code RedPlayer}, {@code GreenPlayer}, {@code YellowPlayer}, {@code BluePlayer}
 *       &ndash; Thin subclasses that supply colour and name to the super-constructor.</li>
 * </ul>
 *
 * <h2>Design patterns</h2>
 * {@link player.PlayerFactory} is the <em>Factory</em> that centralises player creation.
 * {@link player.AbstractPlayer} is the <em>Template Method</em>: it defines the complete
 * player structure so that concrete subclasses need only supply colour and name.
 * Piece selection is handled by the injected strategy (Strategy pattern — see
 * {@link player.strategy}).
 *
 * @see player.strategy
 */
package player;
