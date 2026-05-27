package model;

public class GameConstants {

    // Board
    public static final int BOARD_SIZE = 52;
    public static final int HOME_STRAIGHT_SIZE = 5;
    public static final int NUM_PLAYERS = 4;
    public static final int NUM_PIECES = 4;

    // Positions
    public static final int NO_POSITION = -1;
    public static final int BASE_POSITION = -1;

    // Dice
    public static final int MAX_DICE_ROLL = 6;
    public static final int MIN_DICE_ROLL = 1;
    public static final int TRIPLE_SIX = 3;

    // Effects
    public static final int MYSTERY_CELL_DURATION = 4;
    public static final int EFFECT_DURATION = 4;
    public static final int ENERGISED_MULTIPLIER = 2;
    public static final int SICK_DIVISOR = 2;
    public static final int FROZEN_ROUNDS = 4;
    public static final int CONSECUTIVE_THREES_TO_ESCAPE = 3;
    public static final int ESCAPE_ROLL_VALUE = 3;

    // Start cells
    public static final int RED_START = 0;
    public static final int GREEN_START = 13;
    public static final int YELLOW_START = 26;
    public static final int BLUE_START = 39;

    // Approach cells
    public static final int RED_APPROACH = 50;
    public static final int GREEN_APPROACH = 11;
    public static final int YELLOW_APPROACH = 24;
    public static final int BLUE_APPROACH = 37;

    // Block
    public static final int MIN_BLOCK_SIZE = 2;

    // Capture
    public static final int MIN_CAPTURES_FOR_HOME = 1;

    // Rounds
    public static final int MYSTERY_SPAWN_ROUND = 2;


    // Mystery Cells
    public static final int ALPHA_CELL = 9;
    public static final int BETA_CELL = 27;
    public static final int GAMMA_CELL = 46;
    public static final int FROZEN_ESCAPE_ROLL = 3;
    public static final int TRIPLE_THREE = 3;

    // T-1: CCW approach passes required for home entry
    public static final int APPROACH_PASS_REQUIRED_CCW = 2;
    // T-6: forced block-break move distance on triple six
    public static final int TRIPLE_SIX_BLOCKADE_MOVE = 6;
}
