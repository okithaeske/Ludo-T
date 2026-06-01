package engine.command;

/**
 * <b>Command pattern</b> — encapsulates a single player turn as an executable object.
 * Decouples the invoker ({@link engine.GameEngine}) from the receiver
 * ({@link engine.TurnExecutor}), enabling future extensions such as turn replay,
 * undo, queuing, and logging of the full command sequence.
 *
 * @see ExecuteTurnCommand
 * @see engine.GameEngine#executeRound()
 */
public interface TurnCommand {

    /** Executes the encapsulated turn action. */
    void execute();
}
