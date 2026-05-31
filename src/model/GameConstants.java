package model;

public class GameConstants {

    // Board
    public static final int BOARD_SIZE          = 52;
    public static final int HOME_STRAIGHT_SIZE  = 5;
    public static final int NUM_PLAYERS         = 4;
    public static final int NUM_PIECES          = 4;

    // Sentinel for "not on the standard path" (used for pieces at base, home, or home-straight).
    // PieceState distinguishes BASE from HOME — the position value alone is not enough.
    public static final int NO_POSITION = -1;

    // Dice
    public static final int MAX_DICE_ROLL = 6;
    public static final int MIN_DICE_ROLL = 1;
    public static final int TRIPLE_SIX    = 3;

    // Effects
    public static final int MYSTERY_CELL_DURATION = 4;
    public static final int EFFECT_DURATION       = 4;
    public static final int ENERGISED_MULTIPLIER  = 2;
    public static final int SICK_DIVISOR          = 2;
    public static final int TRIPLE_THREE          = 3;
    public static final int FROZEN_ESCAPE_ROLL    = 3;

    /*
     * Assignment legend: global path numbering starts at the Yellow X square (0)
     * and continues clockwise around the 52 standard cells.
     * From the board layout, the clockwise order of starting X cells is:
     * Yellow -> Blue -> Red -> Green.
     */
    public static final int YELLOW_START = 0;
    public static final int BLUE_START   = 13;
    public static final int RED_START    = 26;
    public static final int GREEN_START  = 39;

    // Each colour's approach cell is two cells before its X in clockwise numbering.
    public static final int YELLOW_APPROACH = 50;
    public static final int BLUE_APPROACH   = 11;
    public static final int RED_APPROACH    = 24;
    public static final int GREEN_APPROACH  = 37;

    // Block
    public static final int MIN_BLOCK_SIZE = 2;

    // Capture
    public static final int MIN_CAPTURES_FOR_HOME = 1;

    // Mystery cells
    public static final int MYSTERY_SPAWN_ROUND          = 2;
    public static final int MYSTERY_FIND_EMPTY_RETRIES   = BOARD_SIZE * 3;

    /*
     * T-11: Alpha/Beta/Gamma are the 9th, 27th and 46th cells from the Yellow
     * approach cell, where the Yellow approach cell itself is counted as 0.
     */
    public static final int ALPHA_CELL = (YELLOW_APPROACH + 9)  % BOARD_SIZE;  // 7
    public static final int BETA_CELL  = (YELLOW_APPROACH + 27) % BOARD_SIZE;  // 25
    public static final int GAMMA_CELL = (YELLOW_APPROACH + 46) % BOARD_SIZE;  // 44

    // T-1: CCW approach passes required for home-straight entry
    public static final int APPROACH_PASS_REQUIRED_CCW = 2;

    // To move from the first home-straight cell through 5 cells and then Home.
    // Position 6 inside the straight = HOME (the calling code maps it to HOME state).
    public static final int HOME_EXIT_DISTANCE = HOME_STRAIGHT_SIZE + 1;

    // Game ends when this many players have finished (4th place is implicit)
    public static final int FINISHING_PLAYERS_TO_END = NUM_PLAYERS - 1;

    // T-6: forced block-break move distance on triple six
    public static final int TRIPLE_SIX_BLOCKADE_MOVE = 6;
}
