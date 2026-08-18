package ui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where every cell sits on a 15&times;15 Ludo cross.
 *
 * <h2>How the track is built</h2>
 * The 52-cell ring is one 13-cell arm repeated four times, each rotated a quarter turn by
 * {@code (x,y) -> (14-y, x)}. Generating it rather than typing 52 coordinates means the four
 * arms cannot drift out of symmetry, and the rotation is also what makes the numbering line up
 * with {@code GameConstants} for free.
 *
 * <p>That alignment is worth checking, because it is the thing that would silently draw a
 * correct game on a wrong board. With index 0 at {@code (1,6)}:
 * <ul>
 *   <li>starts 0 / 13 / 26 / 39 land at the four arm entries, each beside its own yard;</li>
 *   <li>approaches 50 / 11 / 24 / 37 land directly outside their own home straight.</li>
 * </ul>
 * Both fall out of the generated ring with no special-casing, which is a strong sign the
 * numbering in {@code GameConstants} describes this exact layout.
 *
 * <p>Grid coordinates only — no pixels. The canvas scales them, so the board stays sharp at
 * any window size.
 */
public final class BoardGeometry {

    public static final int GRID = 15;
    public static final int TRACK_LENGTH = 52;
    public static final int ARM_LENGTH = 13;
    public static final int HOME_STRAIGHT_LENGTH = 5;

    /** Cell index to grid square, for the 52 shared track cells. */
    private static final List<Point> TRACK = buildTrack();

    /** Colour to its five home-straight squares, running inward towards the centre. */
    private static final Map<String, List<Point>> HOME_STRAIGHTS = Map.of(
            "YELLOW", straight(1, 7, 1, 0),
            "BLUE", straight(7, 1, 0, 1),
            "RED", straight(13, 7, -1, 0),
            "GREEN", straight(7, 13, 0, -1));

    /** Colour to the top-left corner of its 6&times;6 yard. */
    private static final Map<String, Point> YARDS = Map.of(
            "YELLOW", new Point(0, 0),
            "BLUE", new Point(9, 0),
            "RED", new Point(9, 9),
            "GREEN", new Point(0, 9));

    private BoardGeometry() {
        // Static geometry.
    }

    private static List<Point> buildTrack() {
        // One arm: right along row 6, up column 6, then across the top edge.
        List<Point> arm = new ArrayList<>(ARM_LENGTH);
        for (int x = 1; x <= 5; x++) {
            arm.add(new Point(x, 6));
        }
        for (int y = 5; y >= 1; y--) {
            arm.add(new Point(6, y));
        }
        arm.add(new Point(6, 0));
        arm.add(new Point(7, 0));
        arm.add(new Point(8, 0));

        List<Point> track = new ArrayList<>(TRACK_LENGTH);
        List<Point> current = arm;
        for (int quarter = 0; quarter < 4; quarter++) {
            track.addAll(current);
            current = rotate(current);
        }
        return List.copyOf(track);
    }

    /** Quarter turn clockwise about the centre of the grid. */
    private static List<Point> rotate(List<Point> points) {
        List<Point> rotated = new ArrayList<>(points.size());
        for (Point point : points) {
            rotated.add(new Point(GRID - 1 - point.y, point.x));
        }
        return rotated;
    }

    private static List<Point> straight(int startX, int startY, int stepX, int stepY) {
        List<Point> cells = new ArrayList<>(HOME_STRAIGHT_LENGTH);
        for (int i = 0; i < HOME_STRAIGHT_LENGTH; i++) {
            cells.add(new Point(startX + stepX * i, startY + stepY * i));
        }
        return List.copyOf(cells);
    }

    /** Grid square of a track cell. Index is taken modulo the track length. */
    public static Point trackCell(int index) {
        int wrapped = ((index % TRACK_LENGTH) + TRACK_LENGTH) % TRACK_LENGTH;
        return TRACK.get(wrapped);
    }

    /**
     * Grid square of a piece inside a home straight.
     *
     * @param straightPosition 1&ndash;5 as the domain counts it; 6 means Home (the centre)
     */
    public static Point homeStraightCell(String colour, int straightPosition) {
        List<Point> cells = HOME_STRAIGHTS.get(colour.toUpperCase());
        if (cells == null) {
            return centre();
        }
        if (straightPosition >= HOME_STRAIGHT_LENGTH + 1) {
            return centre();
        }
        int index = Math.max(1, straightPosition) - 1;
        return cells.get(Math.min(index, HOME_STRAIGHT_LENGTH - 1));
    }

    public static List<Point> homeStraight(String colour) {
        return HOME_STRAIGHTS.getOrDefault(colour.toUpperCase(), List.of());
    }

    /** Top-left grid square of a colour's 6&times;6 yard. */
    public static Point yard(String colour) {
        return YARDS.getOrDefault(colour.toUpperCase(), new Point(0, 0));
    }

    /**
     * Resting square of the {@code slot}-th piece (0&ndash;3) inside a yard, laid out as a
     * 2&times;2 within the 6&times;6 block.
     */
    public static Point yardSlot(String colour, int slot) {
        Point origin = yard(colour);
        int column = slot % 2;
        int row = slot / 2;
        return new Point(origin.x + 1 + column * 3, origin.y + 1 + row * 3);
    }

    public static Point centre() {
        return new Point(7, 7);
    }

    /** Track cells belonging to a colour's yard block, used to tint the yard. */
    public static boolean isInsideYard(String colour, int gridX, int gridY) {
        Point origin = yard(colour);
        return gridX >= origin.x && gridX < origin.x + 6
                && gridY >= origin.y && gridY < origin.y + 6;
    }
}
