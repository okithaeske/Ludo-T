package model;

import enums.TeleportDest;

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
}
