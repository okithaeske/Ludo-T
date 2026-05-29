package model;

import enums.TeleportDest;

/**
 * <b>DTO</b> — carries the result of a single move validation from {@link engine.RuleEngine}
 * to {@link engine.TurnExecutor}. Contains no behaviour; all fields are set by
 * {@code RuleEngine} and read by {@code TurnExecutor} to decide how to update the board.
 *
 * @see engine.RuleEngine#validateMove(Piece, int)
 * @see engine.RuleEngine#validateBlockMove(model.Block, int)
 * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
 */
public class MoveResult {

    private boolean valid;
    private int targetCell;
    private boolean isCapture;
    private Piece capturedPiece;
    private boolean isSameColourBlocked;
    private int blockedAt;
    private boolean isHome;
    private TeleportDest teleportDest;
    private boolean isLudoTBlocked;
    private boolean isMystery;
    private boolean blockedAtAdjacent;
    private boolean enteringHomeStraight;
    private boolean movingInHomeStraight;
    private int homeStraightPosition;
    private boolean blockCapture;
    private Block defenderBlock;

    public boolean isValid() {
        return valid;
    }

    public int getTargetCell() {
        return targetCell;
    }

    public boolean isCapture() {
        return isCapture;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public boolean isSameColourBlocked() {
        return isSameColourBlocked;
    }

    public int getBlockedAt() {
        return blockedAt;
    }

    public boolean isMystery() {
        return isMystery;
    }

    public boolean isHome() {
        return isHome;
    }

    public TeleportDest getTeleportDest() {
        return teleportDest;
    }

    public boolean isLudoTBlocked() {
        return isLudoTBlocked;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setTargetCell(int targetCell) {
        this.targetCell = targetCell;
    }

    public void setCapture(boolean capture) {
        isCapture = capture;
    }

    public void setCapturedPiece(Piece capturedPiece) {
        this.capturedPiece = capturedPiece;
    }

    public void setSameColourBlocked(boolean sameColourBlocked) {
        isSameColourBlocked = sameColourBlocked;
    }

    public void setBlockedAt(int blockedAt) {
        this.blockedAt = blockedAt;
    }

    public void setHome(boolean home) {
        isHome = home;
    }

    public void setTeleportDest(TeleportDest teleportDest) {
        this.teleportDest = teleportDest;
    }

    public void setLudoTBlocked(boolean ludoTBlocked) {
        isLudoTBlocked = ludoTBlocked;
    }

    public void setMystery(boolean mystery) {
        isMystery = mystery;
    }

    public boolean isBlockedAtAdjacent() {
        return blockedAtAdjacent;
    }

    public void setBlockedAtAdjacent(boolean blockedAtAdjacent) {
        this.blockedAtAdjacent = blockedAtAdjacent;
    }

    public boolean isEnteringHomeStraight() { return enteringHomeStraight; }
    public void setEnteringHomeStraight(boolean enteringHomeStraight) { this.enteringHomeStraight = enteringHomeStraight; }

    public boolean isMovingInHomeStraight() { return movingInHomeStraight; }
    public void setMovingInHomeStraight(boolean movingInHomeStraight) { this.movingInHomeStraight = movingInHomeStraight; }

    public int getHomeStraightPosition() { return homeStraightPosition; }
    public void setHomeStraightPosition(int homeStraightPosition) { this.homeStraightPosition = homeStraightPosition; }

    public boolean isBlockCapture() { return blockCapture; }
    public void setBlockCapture(boolean blockCapture) { this.blockCapture = blockCapture; }

    public Block getDefenderBlock() { return defenderBlock; }
    public void setDefenderBlock(Block defenderBlock) { this.defenderBlock = defenderBlock; }
}
