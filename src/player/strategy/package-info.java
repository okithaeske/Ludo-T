/**
 * Piece-selection strategies (Strategy pattern).
 *
 * <p><b>Start here:</b> {@link player.strategy.PieceSelectionStrategy} is the interface.
 * Each concrete implementation corresponds to one player colour and its behavioural rules.
 *
 * <h2>Strategies</h2>
 * <ul>
 *   <li>{@link player.strategy.AggressiveStrategy} &ndash; <b>Red.</b> Prioritises capturing
 *       the opponent piece nearest that opponent's own home. Brings base pieces only when no
 *       board piece can move.</li>
 *   <li>{@link player.strategy.BlockerStrategy} &ndash; <b>Green.</b> Actively forms and
 *       maintains blocks. Seeks captures only for pieces that still need one to qualify for
 *       home. Falls back to avoiding capture.</li>
 *   <li>{@link player.strategy.RacerStrategy} &ndash; <b>Yellow.</b> Always exits base on a
 *       6, captures only with pieces that need it, then moves the piece closest to home.</li>
 *   <li>{@link player.strategy.MysteryHunterStrategy} &ndash; <b>Blue.</b> CCW pieces seek
 *       the mystery cell; CW pieces avoid landing on it.</li>
 * </ul>
 *
 * <h2>Design pattern</h2>
 * Classic <em>Strategy</em>: {@link player.AbstractPlayer#choosePiece(int, model.Board)}
 * delegates to whichever {@code PieceSelectionStrategy} is wired in by
 * {@link player.PlayerFactory}.
 */
package player.strategy;
