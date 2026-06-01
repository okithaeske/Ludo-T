package model;

import enums.Direction;

/**
 * <b>DTO</b> — carries a single piece relocation planned by
 * {@link engine.RuleEngine#planBlockBreak(player.AbstractPlayer)} back to
 * {@link engine.TurnExecutor} for execution. Immutable; contains no behaviour
 * beyond accessors.
 *
 * @see engine.RuleEngine#planBlockBreak(player.AbstractPlayer)
 * @see engine.TurnExecutor
 */
public class BlockBreakMove {

    private final Piece piece;
    private final int fromPos;
    private final int newPos;
    private final Direction direction;
    private final int distance;

    public BlockBreakMove(Piece piece, int fromPos, int newPos, Direction direction, int distance) {
        this.piece = piece;
        this.fromPos = fromPos;
        this.newPos = newPos;
        this.direction = direction;
        this.distance = distance;
    }

    public Piece getPiece()       { return piece; }
    public int getFromPos()       { return fromPos; }
    public int getNewPos()        { return newPos; }
    public Direction getDirection() { return direction; }
    public int getDistance()      { return distance; }
}
