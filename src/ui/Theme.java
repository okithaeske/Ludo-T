package ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Every colour and font in one place, in two variants.
 *
 * <p>A live dark/light switch means no component may cache a colour in a field — they read
 * from here at paint time, and {@link #toggle()} plus a repaint is the whole mechanism.
 *
 * <p>The four player colours are deliberately not the pure RGB primaries: saturated red and
 * blue on a dark background vibrate badly and are hard to tell apart at piece size, so these
 * are slightly desaturated and lightness-matched to stay distinguishable in both variants.
 */
public final class Theme {

    private static boolean dark = true;

    private Theme() {
        // Static holder.
    }

    public static boolean isDark() {
        return dark;
    }

    public static void toggle() {
        dark = !dark;
    }

    public static void setDark(boolean value) {
        dark = value;
    }

    private static Color pick(Color darkValue, Color lightValue) {
        return dark ? darkValue : lightValue;
    }

    // ── Surfaces ─────────────────────────────────────────────────────────────

    public static Color background() {
        return pick(new Color(0x14, 0x17, 0x1C), new Color(0xF5, 0xF6, 0xF8));
    }

    public static Color panel() {
        return pick(new Color(0x1C, 0x20, 0x27), new Color(0xFF, 0xFF, 0xFF));
    }

    public static Color panelAlt() {
        return pick(new Color(0x23, 0x28, 0x31), new Color(0xEC, 0xEF, 0xF3));
    }

    public static Color border() {
        return pick(new Color(0x33, 0x3A, 0x45), new Color(0xD2, 0xD7, 0xDE));
    }

    // ── Text ─────────────────────────────────────────────────────────────────

    public static Color text() {
        return pick(new Color(0xE6, 0xEA, 0xF0), new Color(0x1B, 0x1F, 0x26));
    }

    public static Color textMuted() {
        return pick(new Color(0x8C, 0x96, 0xA5), new Color(0x66, 0x6E, 0x7A));
    }

    public static Color accent() {
        return pick(new Color(0x5A, 0x9C, 0xF8), new Color(0x1A, 0x6D, 0xD9));
    }

    // ── Status ───────────────────────────────────────────────────────────────

    public static Color ok() {
        return pick(new Color(0x4C, 0xC3, 0x8A), new Color(0x1E, 0x8E, 0x59));
    }

    public static Color warn() {
        return pick(new Color(0xE0, 0xA8, 0x4E), new Color(0xB2, 0x76, 0x11));
    }

    public static Color danger() {
        return pick(new Color(0xE0, 0x6B, 0x6B), new Color(0xC0, 0x39, 0x39));
    }

    // ── Board ────────────────────────────────────────────────────────────────

    public static Color track() {
        return pick(new Color(0x2A, 0x30, 0x3A), new Color(0xFA, 0xFB, 0xFC));
    }

    public static Color trackLine() {
        return pick(new Color(0x3D, 0x45, 0x52), new Color(0xC3, 0xCA, 0xD4));
    }

    public static Color boardCentre() {
        return pick(new Color(0x22, 0x27, 0x30), new Color(0xEE, 0xF1, 0xF5));
    }

    public static Color mystery() {
        return pick(new Color(0xC9, 0x7B, 0xF0), new Color(0x8A, 0x3F, 0xC0));
    }

    /** Alpha / Beta / Gamma effect cells. */
    public static Color special() {
        return pick(new Color(0x5F, 0xB9, 0xC4), new Color(0x2A, 0x82, 0x8E));
    }

    // ── Player colours ───────────────────────────────────────────────────────

    public static Color forColour(String colourName) {
        return switch (colourName == null ? "" : colourName.toUpperCase()) {
            case "RED" -> pick(new Color(0xE5, 0x5B, 0x5B), new Color(0xD1, 0x3A, 0x3A));
            case "GREEN" -> pick(new Color(0x54, 0xBF, 0x7E), new Color(0x24, 0x8F, 0x4C));
            case "YELLOW" -> pick(new Color(0xE3, 0xC1, 0x4E), new Color(0xB8, 0x8E, 0x0E));
            case "BLUE" -> pick(new Color(0x54, 0x94, 0xE0), new Color(0x1F, 0x66, 0xC0));
            default -> textMuted();
        };
    }

    /** A translucent wash of a player's colour, for yards and home straights. */
    public static Color forColourSoft(String colourName) {
        Color base = forColour(colourName);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), dark ? 46 : 38);
    }

    // ── Fonts ────────────────────────────────────────────────────────────────

    public static Font uiFont(int size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    public static Font uiFontBold(int size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    public static Font monoFont(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }
}
