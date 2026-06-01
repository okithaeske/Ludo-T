/**
 * Domain model: board, pieces, blocks, constants, and supporting value objects.
 *
 * <p><b>Start here:</b> {@link model.Board} owns the canonical piece positions and exposes
 * the helper methods ({@code projectPosition}, {@code getBlockAt}, {@code getAdjacentCell})
 * that the engine relies on. {@link model.Piece} represents a single playing piece with
 * intention-revealing mutation methods.
 *
 * <h2>Class responsibilities</h2>
 * <ul>
 *   <li>{@link model.Board} &ndash; Piece placement/removal, approach/start-X look-ups,
 *       board queries, and three shared helpers added during refactoring.</li>
 *   <li>{@link model.Piece} &ndash; Position, direction, effects, and capture count for one
 *       piece. Mutation is exposed through intention-revealing methods
 *       ({@code moveTo}, {@code incrementApproachPass}, {@code tickEffectCountdown},
 *       {@code assignInitialDirection}) rather than bare setters.</li>
 *   <li>{@link model.Block} &ndash; Two same-colour pieces occupying the same cell;
 *       carries block-specific move and capture logic.</li>
 *   <li>{@link model.MoveResult} &ndash; <em>DTO.</em> Returned by
 *       {@link engine.RuleEngine#validateMove(model.Piece, int)}; conveys validity, capture,
 *       home, teleport destination, and home-straight position. No behaviour.</li>
 *   <li>{@link model.BlockMoveResult} &ndash; <em>DTO.</em> Returned by
 *       {@link engine.RuleEngine#resolveBlock(model.Block, int)}; signals whether the block
 *       broke at the approach cell. Immutable, no behaviour.</li>
 *   <li>{@link model.GameConstants} &ndash; All tunable constants (board size, effect
 *       durations, cell IDs, etc.).</li>
 *   <li>{@link model.NoPiece} &ndash; <em>Null Object / Singleton.</em> Returned by strategy
 *       methods when no valid piece exists; eliminates null checks.</li>
 *   <li>{@link model.Dice} &ndash; <em>Singleton</em> dice roller (seeded for tests via
 *       {@link model.RandomInitiator}).</li>
 *   <li>{@link model.MysteryCell} &ndash; Roaming cell active in Ludo-T mode; holds a
 *       teleport destination and a remaining-rounds countdown.</li>
 * </ul>
 */
package model;
