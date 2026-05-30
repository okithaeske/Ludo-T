package model;

import enums.Colour;
import enums.Direction;
import enums.PieceEffect;
import enums.PieceState;

public class Piece {
    private final String id;
    private final Colour colour;
    private int position;
    private PieceState state;
    private Direction direction;
    private int captureCount;
    private PieceEffect activeEffect;
    private int effectRoundsLeft;
    private int approachPassCount;
    private Direction originalDirection;
    private int homeStraightPosition; // 0 = not in straight, 1-5 = position inside straight

    public Piece(String id, Colour colour) {
        this.id = id;
        this.colour = colour;
        this.position = GameConstants.BASE_POSITION;
        this.state = PieceState.BASE;
        this.direction = Direction.CW;
        this.originalDirection = Direction.CW;
        this.captureCount = 0;
        this.activeEffect = PieceEffect.NONE;
        this.effectRoundsLeft = 0;
        this.approachPassCount = 0;
        this.homeStraightPosition = 0;
    }

    public void capture() {
        captureCount++;
    }

    public void reset() {
        position = GameConstants.NO_POSITION;
        state = PieceState.BASE;
        direction = Direction.CW;
        originalDirection = Direction.CW;
        captureCount = 0;
        activeEffect = PieceEffect.NONE;
        effectRoundsLeft = 0;
        approachPassCount = 0;
        homeStraightPosition = 0;
    }

    public void applyEffect(PieceEffect effect) {
        this.activeEffect = effect;
        this.effectRoundsLeft = GameConstants.EFFECT_DURATION;
    }

    public int getEffectiveRoll(int roll) {
        if (activeEffect == PieceEffect.ENERGISED) {
            return roll * GameConstants.ENERGISED_MULTIPLIER;
        } else if (activeEffect == PieceEffect.SICK) {
            return roll / GameConstants.SICK_DIVISOR;
        }
        return roll;
    }

    public boolean isMovementRestricted() {
        return activeEffect == PieceEffect.FROZEN && effectRoundsLeft > 0;
    }

    public boolean isNull() {
        return false;
    }

    public boolean isAtBase() {
        return state == PieceState.BASE;
    }

    public boolean canEnterHomeStraight() {
        return captureCount >= 1;
    }

    public String getId() { return id; }
    public Colour getColour() { return colour; }
    public int getPosition() { return position; }
    public PieceState getState() { return state; }
    public Direction getDirection() { return direction; }
    public int getCaptureCount() { return captureCount; }
    public PieceEffect getActiveEffect() { return activeEffect; }
    public int getEffectRoundsLeft() { return effectRoundsLeft; }
    public int getApproachPassCount() { return approachPassCount; }

    /**
     * Moves this piece to {@code position} on the standard board.
     * All board-position mutations go through this method.
     *
     * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
     */
    public void moveTo(int position) { this.position = position; }

    /**
     * Increments the CCW approach-pass counter used to gate home entry (Rule T-1 §4.2).
     *
     * @see engine.TurnExecutor#executeTurn(player.AbstractPlayer, boolean)
     */
    public void incrementApproachPass() { approachPassCount++; }

    /**
     * Decrements the active-effect countdown by one round. Clears the effect when the
     * counter reaches zero.
     *
     * @see engine.EffectHandler#tickFrozenPiece(player.AbstractPlayer)
     */
    public void tickEffectCountdown() {
        if (activeEffect == PieceEffect.NONE || effectRoundsLeft <= 0) {
            return;
        }
        effectRoundsLeft--;
        if (effectRoundsLeft <= 0) {
            activeEffect = PieceEffect.NONE;
            effectRoundsLeft = 0;
        }
    }

    /**
     * Sets both {@code direction} and {@code originalDirection} to {@code d}. Called once
     * per piece when it first leaves base (coin toss — Rule T-1 §3.1).
     *
     * @see engine.EffectHandler#performCoinToss(Piece)
     */
    public void assignInitialDirection(Direction d) {
        this.direction = d;
        this.originalDirection = d;
    }

    public void setState(PieceState state) { this.state = state; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Direction getOriginalDirection() { return originalDirection; }

    public int getHomeStraightPosition() { return homeStraightPosition; }
    public void setHomeStraightPosition(int pos) { this.homeStraightPosition = pos; }

    public boolean isInHomeStraight() { return homeStraightPosition > 0; }

    public void leaveStandardPathForHomeStraight(int homeStraightPosition) {
        this.position = GameConstants.NO_POSITION;
        this.homeStraightPosition = homeStraightPosition;
        this.state = PieceState.ACTIVE;
    }

    public void clearMovementEffects() {
        if (activeEffect == PieceEffect.ENERGISED || activeEffect == PieceEffect.SICK) {
            activeEffect = PieceEffect.NONE;
            effectRoundsLeft = 0;
        }
    }
}
