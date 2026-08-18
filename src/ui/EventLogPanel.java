package ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

/**
 * The live feed of game events, colour-coded by which player they concern.
 *
 * <p>Two details keep it usable during a fast game. The document is capped, because a game can
 * emit thousands of lines and an uncapped pane would grow until the client stalled. And
 * auto-scroll can be switched off, so reading back through the log does not fight against new
 * lines dragging the view to the bottom.
 */
public final class EventLogPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Oldest lines are trimmed past this, so memory stays flat over a long game. */
    private static final int MAX_CHARACTERS = 120_000;
    private static final int TRIM_TO = 90_000;

    private final JTextPane pane = new JTextPane();
    private final JCheckBox autoScroll = new JCheckBox("Follow", true);
    private final JLabel heading = new JLabel("Event log");

    public EventLogPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Theme.panel());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        heading.setFont(Theme.uiFontBold(13));
        header.add(heading, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        autoScroll.setFont(Theme.uiFont(11));
        autoScroll.setOpaque(false);
        autoScroll.setFocusable(false);
        autoScroll.setToolTipText("Keep the newest line in view");
        controls.add(autoScroll);

        JButton clear = new JButton("Clear");
        clear.setFont(Theme.uiFont(11));
        clear.setFocusable(false);
        clear.addActionListener(event -> clear());
        controls.add(clear);
        header.add(controls, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        pane.setEditable(false);
        pane.setFont(Theme.monoFont(11));
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border()));
        add(scroll, BorderLayout.CENTER);

        applyTheme();
    }

    /** Appends one line, colouring it by the player it mentions. */
    public void append(String line) {
        StyledDocument document = pane.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, colourFor(line));

        try {
            document.insertString(document.getLength(), line + "\n", style);
            trimIfTooLong(document);
        } catch (BadLocationException e) {
            // The document is only ever appended to on the EDT, so this cannot happen in
            // practice; dropping the line is better than tearing down the UI if it ever does.
            return;
        }

        if (autoScroll.isSelected()) {
            pane.setCaretPosition(document.getLength());
        }
    }

    public void appendSystem(String line) {
        StyledDocument document = pane.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, Theme.accent());
        StyleConstants.setItalic(style, true);
        try {
            document.insertString(document.getLength(), line + "\n", style);
            trimIfTooLong(document);
        } catch (BadLocationException e) {
            return;
        }
        if (autoScroll.isSelected()) {
            pane.setCaretPosition(document.getLength());
        }
    }

    private void trimIfTooLong(StyledDocument document) throws BadLocationException {
        if (document.getLength() <= MAX_CHARACTERS) {
            return;
        }
        document.remove(0, document.getLength() - TRIM_TO);
    }

    /** Lines begin with the player's colour, which is enough to tint the whole line. */
    private Color colourFor(String line) {
        String lower = line.toLowerCase();
        if (lower.startsWith("red")) return Theme.forColour("RED");
        if (lower.startsWith("green")) return Theme.forColour("GREEN");
        if (lower.startsWith("yellow")) return Theme.forColour("YELLOW");
        if (lower.startsWith("blue")) return Theme.forColour("BLUE");
        if (lower.startsWith("mystery")) return Theme.mystery();
        if (lower.startsWith("final standings") || lower.startsWith("game started")) {
            return Theme.accent();
        }
        return Theme.textMuted();
    }

    public void clear() {
        pane.setText("");
    }

    public void applyTheme() {
        setBackground(Theme.panel());
        heading.setForeground(Theme.text());
        autoScroll.setForeground(Theme.text());
        pane.setBackground(Theme.panelAlt());
        pane.setForeground(Theme.text());
        pane.setCaretColor(Theme.text());
        repaint();
    }
}
