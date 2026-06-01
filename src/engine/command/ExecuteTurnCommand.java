package engine.command;

import engine.TurnExecutor;
import player.AbstractPlayer;

/**
 * <b>Command pattern</b> concrete command — wraps a single call to
 * {@link TurnExecutor#executeTurn(AbstractPlayer, boolean)} along with its
 * receiver and parameters so that {@link engine.GameEngine} remains decoupled
 * from turn-execution details.
 *
 * @see TurnCommand
 * @see engine.GameEngine#executeRound()
 */
public class ExecuteTurnCommand implements TurnCommand {

    private final TurnExecutor executor;
    private final AbstractPlayer player;
    private final boolean isExtraRoll;

    public ExecuteTurnCommand(TurnExecutor executor, AbstractPlayer player, boolean isExtraRoll) {
        this.executor = executor;
        this.player = player;
        this.isExtraRoll = isExtraRoll;
    }

    @Override
    public void execute() {
        executor.executeTurn(player, isExtraRoll);
    }
}
