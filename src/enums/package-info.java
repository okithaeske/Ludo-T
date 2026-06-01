/**
 * Shared enumerations used throughout the codebase.
 *
 * <ul>
 *   <li>{@link enums.Colour} &ndash; {@code RED}, {@code GREEN}, {@code YELLOW}, {@code BLUE};
 *       identifies a player and their pieces.</li>
 *   <li>{@link enums.Direction} &ndash; {@code CW} (clockwise) or {@code CCW}
 *       (counterclockwise); determined by coin toss when a piece first leaves base.</li>
 *   <li>{@link enums.GameMode} &ndash; {@code CLASSIC} or {@code LUDO_T}; gates the
 *       mystery-cell, coin-toss, block, and frozen mechanics.</li>
 *   <li>{@link enums.PieceEffect} &ndash; {@code NONE}, {@code FROZEN}, {@code ENERGISED},
 *       {@code SICK}, {@code DIR_FLIP}; active status effect on a piece.</li>
 *   <li>{@link enums.PieceState} &ndash; {@code BASE}, {@code ACTIVE}, {@code HOME};
 *       <em>State pattern</em> — drives different behaviour in {@link engine.RuleEngine},
 *       {@link player.AbstractPlayer}, and {@link engine.WinTracker} based on which state
 *       a piece is in.</li>
 *   <li>{@link enums.TeleportDest} &ndash; {@code BASE}, {@code START_X}, {@code APPROACH},
 *       {@code ALPHA}, {@code BETA}, {@code GAMMA}; destination encoded in a
 *       {@link model.MoveResult} when a mystery-cell teleport fires.</li>
 * </ul>
 */
package enums;
