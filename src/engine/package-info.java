/**
 * Game orchestration and rule validation.
 *
 * <p><b>Start here:</b> {@link engine.GameEngine#startGame()} is the single entry point.
 * Everything else is reached through the call chain that originates there.
 *
 * <h2>Class responsibilities</h2>
 * <ul>
 *   <li>{@link engine.GameEngine} &ndash; <em>Facade.</em> Game lifecycle: starts the game,
 *       drives the round loop, handles mystery-cell spawning. Delegates all turn and win
 *       logic to collaborators.</li>
 *   <li>{@link engine.TurnExecutor} &ndash; Executes one turn per player: rolls dice, applies
 *       frozen/escape rules, validates the move, and applies the resulting board changes.</li>
 *   <li>{@link engine.EffectHandler} &ndash; Isolated effect logic: frozen countdown, frozen
 *       escape to base, coin toss on base exit, and mystery-cell teleport effects.</li>
 *   <li>{@link engine.WinTracker} &ndash; Records the finishing order; signals game-over when
 *       enough players have finished.</li>
 *   <li>{@link engine.FirstPlayerSelector} &ndash; Rolls dice for all players and breaks ties
 *       to determine who goes first.</li>
 *   <li>{@link engine.RuleEngine} &ndash; Pure rule validation: {@code validateMove},
 *       {@code validateBlockMove}, {@code resolveBlock}, home-straight entry, captures.</li>
 *   <li>{@link engine.TurnManager} &ndash; Owns the game's dice, consecutive-roll tracking
 *       (triple six / triple three), extra-roll flag, turn order.</li>
 * </ul>
 *
 * <h2>Design pattern</h2>
 * After the refactor {@code GameEngine} acts as a <em>Facade</em> over the five collaborator
 * classes above. External code (e.g. {@code Main}) only needs to call
 * {@code GameEngine.startGame()}.
 */
package engine;
