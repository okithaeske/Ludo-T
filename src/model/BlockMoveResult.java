package model;

/**
 * <b>DTO</b> — carries the result of {@link engine.RuleEngine#resolveBlock(model.Block, int)}
 * back to {@link engine.TurnExecutor}. Immutable; contains no behaviour beyond accessors.
 *
 * @see engine.RuleEngine#resolveBlock(model.Block, int)
 * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
 */
public class BlockMoveResult {
    private final boolean brokeAtApproach;
    private final int approachCell;

    public BlockMoveResult(boolean brokeAtApproach, int approachCell) {
        this.brokeAtApproach = brokeAtApproach;
        this.approachCell = approachCell;
    }

    public boolean brokeAtApproach() {
        return brokeAtApproach;
    }

    public int getApproachCell() {
        return approachCell;
    }
}
