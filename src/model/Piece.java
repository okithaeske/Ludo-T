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
    }

    public void move(int steps) {
        position += getEffectiveRoll(steps);
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
    }

    public void applyEffect(PieceEffect effect) {
        this.activeEffect = effect;
        this.effectRoundsLeft = GameConstants.EFFECT_DURATION;
    }

    public int getEffectiveRoll(int roll) {
        if (activeEffect == PieceEffect.ENERGISED) {
            return roll * 2;
        } else if (activeEffect == PieceEffect.SICK) {
            return roll / 2;
        }
        return roll;
    }

    public boolean isNull() {
        return false;
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

    public void setPosition(int position) { this.position = position; }
    public void setState(PieceState state) { this.state = state; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Direction getOriginalDirection() { return originalDirection; }
    public void setOriginalDirection(Direction direction) { this.originalDirection = direction; }
    public void setApproachPassCount(int count) { this.approachPassCount = count; }
    public void setEffectRoundsLeft(int rounds) { this.effectRoundsLeft = rounds; }
}
