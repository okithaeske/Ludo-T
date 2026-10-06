package ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A button that paints itself from {@link Theme} instead of leaving it to the look and feel.
 *
 * <p>The Windows look and feel draws its own white button face and ignores
 * {@code setBackground}, but still honours {@code setForeground}. Asking it for the dark
 * theme's near-white text therefore produced near-white text on a white button — legible only
 * while the button was disabled and greyed. Painting the face here is the only way to keep
 * the two colours a matching pair in both themes.
 *
 * <p>Colours are read at paint time, so a theme switch needs nothing but a repaint.
 */
final class ThemedButton extends JButton {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 8;

    ThemedButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        // Only padding: it is what gives the button its preferred size around the text.
        setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        ButtonModel model = getModel();
        boolean pressed = isEnabled() && model.isArmed() && model.isPressed();
        boolean hovered = isEnabled() && model.isRollover();

        Color face = pressed ? Theme.accent() : hovered ? Theme.border() : Theme.panelAlt();
        g.setColor(face);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g.setColor(Theme.border());
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);

        g.setColor(inkFor(pressed));
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        String text = getText() == null ? "" : getText();
        int x = (getWidth() - metrics.stringWidth(text)) / 2;
        int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(text, x, y);
        g.dispose();
    }

    private Color inkFor(boolean pressed) {
        if (!isEnabled()) {
            // Faded rather than a different hue, so it still reads as text and as unavailable.
            Color muted = Theme.textMuted();
            return new Color(muted.getRed(), muted.getGreen(), muted.getBlue(), 140);
        }
        // The accent face is the one surface the normal text colour does not sit well on.
        return pressed ? Theme.panel() : Theme.text();
    }
}
