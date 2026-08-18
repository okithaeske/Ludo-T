package ui;

import shared.BoardSnapshot;
import shared.PieceDto;
import shared.PlayerDto;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints the board.
 *
 * <h2>Why pieces are animated rather than redrawn in place</h2>
 * Snapshots arrive one per round, so a piece that moved six cells would otherwise teleport,
 * and with four players moving each round it becomes impossible to see <em>what happened</em>
 * — only what is now true. Interpolating between the last two snapshots turns each round into
 * a movement you can follow. The animation is presentation only: it never changes what the
 * board reports, and a snapshot arriving mid-animation simply becomes the new target.
 *
 * <h2>Threading</h2>
 * Everything here runs on the EDT. Snapshots reach {@link #showSnapshot} already marshalled by
 * {@code ServerConnection}, and the animation is driven by a Swing {@link Timer}, which also
 * fires on the EDT — so no field in this class is ever touched by two threads.
 */
public final class BoardCanvas extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Long enough to follow, short enough not to lag behind a fast game. */
    private static final int ANIMATION_MILLIS = 260;
    private static final int FRAME_MILLIS = 16;

    /** Alpha, Beta and Gamma track cells, from GameConstants. */
    private static final Map<Integer, String> SPECIAL_CELLS =
            Map.of(7, "α", 25, "β", 44, "γ");

    private BoardSnapshot snapshot;

    /** Where each piece is being drawn right now, in grid coordinates. */
    private final Map<String, Point2D.Double> drawnAt = new HashMap<>();
    private final Map<String, Point2D.Double> movingFrom = new HashMap<>();
    private final Map<String, Point2D.Double> movingTo = new HashMap<>();

    private final Timer animator;
    private long animationStart;
    private double pulse;

    public BoardCanvas() {
        setOpaque(true);
        // Kept modest so the side panels are never squeezed off a scaled display; the board
        // scales up freely into whatever space it is actually given.
        setPreferredSize(new Dimension(460, 460));
        setMinimumSize(new Dimension(360, 360));
        ToolTipManager.sharedInstance().registerComponent(this);

        animator = new Timer(FRAME_MILLIS, e -> onFrame());
        animator.start();
    }

    /** Accepts a new board. Safe to call as often as snapshots arrive. */
    public void showSnapshot(BoardSnapshot next) {
        this.snapshot = next;
        retarget();
        repaint();
    }

    public void clear() {
        snapshot = null;
        drawnAt.clear();
        movingFrom.clear();
        movingTo.clear();
        repaint();
    }

    private void onFrame() {
        // The mystery cell pulses continuously, so a frame is always worth drawing while a
        // board is on screen — but only then, so an idle client costs nothing.
        if (snapshot == null) {
            return;
        }
        pulse = (System.currentTimeMillis() % 2000) / 2000.0;
        repaint();
    }

    private void retarget() {
        movingFrom.clear();
        movingTo.clear();
        animationStart = System.currentTimeMillis();

        Map<String, Point2D.Double> targets = computeTargets();
        for (Map.Entry<String, Point2D.Double> entry : targets.entrySet()) {
            String pieceId = entry.getKey();
            Point2D.Double target = entry.getValue();
            Point2D.Double current = drawnAt.get(pieceId);
            if (current == null) {
                // First sighting: appear in place rather than sliding in from nowhere.
                drawnAt.put(pieceId, target);
            } else {
                movingFrom.put(pieceId, current);
                movingTo.put(pieceId, target);
            }
        }
    }

    /**
     * Resolves every piece to a grid position, fanning out pieces that share a square so a
     * stack of four is still visibly four.
     */
    private Map<String, Point2D.Double> computeTargets() {
        Map<String, Point2D.Double> targets = new HashMap<>();
        if (snapshot == null) {
            return targets;
        }

        Map<Point, List<String>> occupancy = new HashMap<>();
        Map<String, Point> squareOf = new HashMap<>();

        for (PlayerDto player : snapshot.players()) {
            int baseSlot = 0;
            for (PieceDto piece : player.pieces()) {
                Point square = squareFor(player.colour(), piece, baseSlot);
                if ("BASE".equals(piece.state())) {
                    baseSlot++;
                }
                squareOf.put(piece.id(), square);
                occupancy.computeIfAbsent(square, key -> new ArrayList<>()).add(piece.id());
            }
        }

        for (Map.Entry<String, Point> entry : squareOf.entrySet()) {
            Point square = entry.getValue();
            List<String> sharing = occupancy.get(square);
            int slot = sharing.indexOf(entry.getKey());
            targets.put(entry.getKey(), fanOut(square, slot, sharing.size()));
        }
        return targets;
    }

    private Point squareFor(String colour, PieceDto piece, int baseSlot) {
        if ("HOME".equals(piece.state())) {
            return BoardGeometry.centre();
        }
        if (piece.inHomeStraight()) {
            return BoardGeometry.homeStraightCell(colour, piece.homeStraightPosition());
        }
        if ("BASE".equals(piece.state()) || piece.position() < 0) {
            return BoardGeometry.yardSlot(colour, Math.min(baseSlot, 3));
        }
        return BoardGeometry.trackCell(piece.position());
    }

    /** Spreads {@code count} pieces around the centre of one square. */
    private Point2D.Double fanOut(Point square, int slot, int count) {
        if (count <= 1) {
            return new Point2D.Double(square.x, square.y);
        }
        double radius = 0.20;
        double angle = (2 * Math.PI * slot) / count - Math.PI / 2;
        return new Point2D.Double(
                square.x + radius * Math.cos(angle),
                square.y + radius * Math.sin(angle));
    }

    private double animationProgress() {
        long elapsed = System.currentTimeMillis() - animationStart;
        if (elapsed >= ANIMATION_MILLIS) {
            return 1.0;
        }
        double linear = elapsed / (double) ANIMATION_MILLIS;
        // Ease-out: quick departure, gentle arrival, which reads as movement rather than a jump.
        return 1 - Math.pow(1 - linear, 3);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Theme.background());
        g.fillRect(0, 0, getWidth(), getHeight());

        double cell = cellSize();
        double originX = (getWidth() - cell * BoardGeometry.GRID) / 2.0;
        double originY = (getHeight() - cell * BoardGeometry.GRID) / 2.0;

        drawYards(g, originX, originY, cell);
        drawHomeStraights(g, originX, originY, cell);
        drawTrack(g, originX, originY, cell);
        drawCentre(g, originX, originY, cell);
        drawMysteryCell(g, originX, originY, cell);
        drawPieces(g, originX, originY, cell);

        if (snapshot == null) {
            drawPlaceholder(g);
        }

        g.dispose();
    }

    private double cellSize() {
        return Math.min(getWidth(), getHeight()) / (double) BoardGeometry.GRID;
    }

    /**
     * The hint sits below the board rather than across it. Centred, it landed on the HOME
     * square and read as a label for it.
     */
    private void drawPlaceholder(Graphics2D g) {
        String message = "Select a game from the lobby, or create one";
        g.setFont(Theme.uiFont(13));
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();

        double cell = cellSize();
        double boardBottom = (getHeight() - cell * BoardGeometry.GRID) / 2.0
                + cell * BoardGeometry.GRID;
        int pillHeight = textHeight + 12;
        int y = (int) Math.min(getHeight() - pillHeight - 8, boardBottom + 10);
        int x = (getWidth() - textWidth) / 2 - 14;

        g.setColor(Theme.panelAlt());
        g.fillRoundRect(x, y, textWidth + 28, pillHeight, pillHeight, pillHeight);
        g.setColor(Theme.border());
        g.drawRoundRect(x, y, textWidth + 28, pillHeight, pillHeight, pillHeight);

        g.setColor(Theme.textMuted());
        g.drawString(message, (getWidth() - textWidth) / 2,
                y + pillHeight - g.getFontMetrics().getDescent() - 5);
    }

    private void drawYards(Graphics2D g, double ox, double oy, double cell) {
        for (String colour : List.of("YELLOW", "BLUE", "RED", "GREEN")) {
            Point origin = BoardGeometry.yard(colour);
            RoundRectangle2D yard = new RoundRectangle2D.Double(
                    ox + origin.x * cell, oy + origin.y * cell,
                    cell * 6, cell * 6, cell * 0.6, cell * 0.6);

            g.setColor(Theme.forColourSoft(colour));
            g.fill(yard);
            g.setColor(Theme.forColour(colour));
            g.setStroke(new BasicStroke((float) (cell * 0.06)));
            g.draw(yard);

            // The four resting slots, so an empty yard still reads as "four pieces live here".
            for (int slot = 0; slot < 4; slot++) {
                Point square = BoardGeometry.yardSlot(colour, slot);
                double diameter = cell * 0.62;
                double cx = ox + (square.x + 0.5) * cell - diameter / 2;
                double cy = oy + (square.y + 0.5) * cell - diameter / 2;
                g.setColor(Theme.panel());
                g.fill(new Ellipse2D.Double(cx, cy, diameter, diameter));
                g.setColor(Theme.forColour(colour));
                g.setStroke(new BasicStroke((float) (cell * 0.04)));
                g.draw(new Ellipse2D.Double(cx, cy, diameter, diameter));
            }
        }
    }

    private void drawHomeStraights(Graphics2D g, double ox, double oy, double cell) {
        for (String colour : List.of("YELLOW", "BLUE", "RED", "GREEN")) {
            for (Point square : BoardGeometry.homeStraight(colour)) {
                drawCell(g, ox, oy, cell, square, Theme.forColourSoft(colour));
            }
        }
    }

    private void drawTrack(Graphics2D g, double ox, double oy, double cell) {
        for (int index = 0; index < BoardGeometry.TRACK_LENGTH; index++) {
            Point square = BoardGeometry.trackCell(index);
            drawCell(g, ox, oy, cell, square, Theme.track());
        }

        // Start squares get their owner's colour so entry points are obvious.
        Map<Integer, String> starts = Map.of(0, "YELLOW", 13, "BLUE", 26, "RED", 39, "GREEN");
        for (Map.Entry<Integer, String> entry : starts.entrySet()) {
            Point square = BoardGeometry.trackCell(entry.getKey());
            drawCell(g, ox, oy, cell, square, Theme.forColourSoft(entry.getValue()));
            drawCellLabel(g, ox, oy, cell, square, "X", Theme.forColour(entry.getValue()));
        }

        for (Map.Entry<Integer, String> entry : SPECIAL_CELLS.entrySet()) {
            Point square = BoardGeometry.trackCell(entry.getKey());
            drawCellLabel(g, ox, oy, cell, square, entry.getValue(), Theme.special());
        }
    }

    private void drawCell(Graphics2D g, double ox, double oy, double cell, Point square,
                          Color fill) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(
                ox + square.x * cell + cell * 0.06,
                oy + square.y * cell + cell * 0.06,
                cell * 0.88, cell * 0.88, cell * 0.24, cell * 0.24);
        g.setColor(fill);
        g.fill(shape);
        g.setColor(Theme.trackLine());
        g.setStroke(new BasicStroke((float) (cell * 0.035)));
        g.draw(shape);
    }

    private void drawCellLabel(Graphics2D g, double ox, double oy, double cell, Point square,
                               String label, Color colour) {
        g.setColor(colour);
        g.setFont(Theme.uiFontBold((int) Math.max(9, cell * 0.42)));
        int width = g.getFontMetrics().stringWidth(label);
        int height = g.getFontMetrics().getAscent();
        g.drawString(label,
                (float) (ox + (square.x + 0.5) * cell - width / 2.0),
                (float) (oy + (square.y + 0.5) * cell + height / 2.5));
    }

    private void drawCentre(Graphics2D g, double ox, double oy, double cell) {
        double size = cell * 3;
        double x = ox + 6 * cell;
        double y = oy + 6 * cell;

        RoundRectangle2D home = new RoundRectangle2D.Double(x, y, size, size, cell, cell);
        g.setColor(Theme.boardCentre());
        g.fill(home);
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke((float) (cell * 0.05)));
        g.draw(home);

        g.setColor(Theme.textMuted());
        g.setFont(Theme.uiFontBold((int) Math.max(10, cell * 0.5)));
        String label = "HOME";
        int width = g.getFontMetrics().stringWidth(label);
        g.drawString(label, (float) (x + size / 2 - width / 2.0), (float) (y + size / 2 + cell * 0.18));
    }

    private void drawMysteryCell(Graphics2D g, double ox, double oy, double cell) {
        if (snapshot == null || !snapshot.hasMysteryCell()) {
            return;
        }
        Point square = BoardGeometry.trackCell(snapshot.mysteryPosition());

        // A slow pulse, because the cell is a hazard that moves every few rounds and needs to
        // pull the eye without competing with the pieces.
        double swell = 0.5 + 0.5 * Math.sin(pulse * 2 * Math.PI);
        double inset = cell * (0.02 + 0.05 * swell);

        RoundRectangle2D shape = new RoundRectangle2D.Double(
                ox + square.x * cell + inset, oy + square.y * cell + inset,
                cell - inset * 2, cell - inset * 2, cell * 0.3, cell * 0.3);

        Color base = Theme.mystery();
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
                (int) (90 + 90 * swell)));
        g.fill(shape);
        g.setColor(base);
        g.setStroke(new BasicStroke((float) (cell * 0.07)));
        g.draw(shape);

        drawCellLabel(g, ox, oy, cell, square, "?", Theme.text());
    }

    private void drawPieces(Graphics2D g, double ox, double oy, double cell) {
        if (snapshot == null) {
            return;
        }
        double progress = animationProgress();

        for (PlayerDto player : snapshot.players()) {
            for (PieceDto piece : player.pieces()) {
                Point2D.Double position = positionOf(piece.id(), progress);
                if (position == null) {
                    continue;
                }
                drawnAt.put(piece.id(), position);
                drawPiece(g, ox, oy, cell, position, player.colour(), piece);
            }
        }
    }

    private Point2D.Double positionOf(String pieceId, double progress) {
        Point2D.Double from = movingFrom.get(pieceId);
        Point2D.Double to = movingTo.get(pieceId);
        if (from == null || to == null) {
            return drawnAt.get(pieceId);
        }
        return new Point2D.Double(
                from.x + (to.x - from.x) * progress,
                from.y + (to.y - from.y) * progress);
    }

    private void drawPiece(Graphics2D g, double ox, double oy, double cell,
                           Point2D.Double position, String colour, PieceDto piece) {
        double diameter = cell * 0.56;
        double cx = ox + (position.x + 0.5) * cell;
        double cy = oy + (position.y + 0.5) * cell;

        Ellipse2D body = new Ellipse2D.Double(
                cx - diameter / 2, cy - diameter / 2, diameter, diameter);

        // Shadow first, so stacked pieces stay separable.
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(cx - diameter / 2 + cell * 0.03,
                cy - diameter / 2 + cell * 0.04, diameter, diameter));

        g.setColor(Theme.forColour(colour));
        g.fill(body);

        g.setColor(effectOutline(piece));
        g.setStroke(new BasicStroke((float) (cell * 0.06)));
        g.draw(body);

        if (!"NONE".equals(piece.effect())) {
            drawEffectBadge(g, cx, cy, diameter, cell, piece);
        }
    }

    /** Effects are shown as an outline colour rather than an icon, to survive small cells. */
    private Color effectOutline(PieceDto piece) {
        return switch (piece.effect()) {
            case "FROZEN" -> Theme.accent();
            case "ENERGISED" -> Theme.ok();
            case "SICK" -> Theme.warn();
            default -> Theme.panel();
        };
    }

    private void drawEffectBadge(Graphics2D g, double cx, double cy, double diameter,
                                 double cell, PieceDto piece) {
        String symbol = switch (piece.effect()) {
            case "FROZEN" -> "*";
            case "ENERGISED" -> "+";
            case "SICK" -> "-";
            default -> "";
        };
        if (symbol.isEmpty()) {
            return;
        }
        g.setColor(Theme.text());
        g.setFont(Theme.uiFontBold((int) Math.max(8, cell * 0.34)));
        int width = g.getFontMetrics().stringWidth(symbol);
        g.drawString(symbol, (float) (cx - width / 2.0), (float) (cy + diameter * 0.18));
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        double cell = cellSize();
        double originX = (getWidth() - cell * BoardGeometry.GRID) / 2.0;
        double originY = (getHeight() - cell * BoardGeometry.GRID) / 2.0;

        int gridX = (int) Math.floor((event.getX() - originX) / cell);
        int gridY = (int) Math.floor((event.getY() - originY) / cell);

        for (int index = 0; index < BoardGeometry.TRACK_LENGTH; index++) {
            Point square = BoardGeometry.trackCell(index);
            if (square.x == gridX && square.y == gridY) {
                String extra = SPECIAL_CELLS.containsKey(index)
                        ? " (" + SPECIAL_CELLS.get(index) + ")" : "";
                return "Cell " + index + extra + occupantsAt(index);
            }
        }
        return null;
    }

    private String occupantsAt(int cellIndex) {
        if (snapshot == null) {
            return "";
        }
        List<String> here = new ArrayList<>();
        for (PlayerDto player : snapshot.players()) {
            for (PieceDto piece : player.pieces()) {
                if (!piece.inHomeStraight() && piece.position() == cellIndex) {
                    here.add(piece.id());
                }
            }
        }
        return here.isEmpty() ? "" : " — " + String.join(", ", here);
    }
}
