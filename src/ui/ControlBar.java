package ui;

import shared.Command;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Start / Pause / Resume / Step / Abort, plus a speed slider.
 *
 * <p>Buttons are enabled from the selected game's state rather than from what this client last
 * clicked, because another client may have paused the game a moment ago. Deriving the controls
 * from server state is what stops two clients disagreeing about what is possible.
 */
public final class ControlBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MIN_TICK_MILLIS = 0;
    private static final int MAX_TICK_MILLIS = 1000;

    private final Map<Command, JButton> buttons = new LinkedHashMap<>();
    private final JSlider speed = new JSlider(MIN_TICK_MILLIS, MAX_TICK_MILLIS, 250);
    private final JLabel speedLabel = new JLabel();
    private final JLabel gameLabel = new JLabel("No game selected");

    private Consumer<Command> commandListener = command -> { };
    private BiConsumer<String, Long> speedListener = (gameId, millis) -> { };
    private String gameId;

    public ControlBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
        setBackground(Theme.panel());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()));

        gameLabel.setFont(Theme.uiFontBold(12));
        add(gameLabel);
        add(Box.createHorizontalStrut(8));

        addButton(Command.START_GAME, "Start", "Begin ticking rounds  (Ctrl+R)");
        addButton(Command.PAUSE_GAME, "Pause", "Stop ticking, keep the board  (Ctrl+P)");
        addButton(Command.RESUME_GAME, "Resume", "Continue from where it paused  (Ctrl+P)");
        addButton(Command.STEP_ROUND, "Step", "Advance exactly one round  (Ctrl+.)");
        addButton(Command.ABORT_GAME, "Abort", "End this game permanently");

        add(Box.createHorizontalStrut(16));

        JLabel speedTitle = new JLabel("Speed");
        speedTitle.setFont(Theme.uiFont(12));
        speedTitle.setForeground(Theme.textMuted());
        add(speedTitle);

        speed.setPreferredSize(new Dimension(150, 24));
        speed.setBackground(Theme.panel());
        speed.setToolTipText("Delay between rounds — left is faster");
        speed.addChangeListener(event -> {
            updateSpeedLabel();
            // Fire only when the drag ends, or every intermediate value becomes a request.
            if (!speed.getValueIsAdjusting() && gameId != null) {
                speedListener.accept(gameId, (long) speed.getValue());
            }
        });
        add(speed);

        speedLabel.setFont(Theme.monoFont(12));
        add(speedLabel);

        updateSpeedLabel();
        setSelectedGame(null, null);
        applyTheme();
    }

    private void addButton(Command command, String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFont(Theme.uiFont(12));
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(event -> commandListener.accept(command));
        buttons.put(command, button);
        add(button);
    }

    public void setCommandListener(Consumer<Command> listener) {
        this.commandListener = listener;
    }

    public void setSpeedListener(BiConsumer<String, Long> listener) {
        this.speedListener = listener;
    }

    private void updateSpeedLabel() {
        int value = speed.getValue();
        speedLabel.setText(value == 0 ? "max" : value + " ms");
        speedLabel.setForeground(Theme.textMuted());
    }

    /**
     * Points the bar at a game and enables only the commands its state allows.
     *
     * @param state one of the {@code SessionState} names, or null when nothing is selected
     */
    public void setSelectedGame(String gameId, String state) {
        this.gameId = gameId;

        if (gameId == null) {
            gameLabel.setText("No game selected");
            gameLabel.setForeground(Theme.textMuted());
            buttons.values().forEach(button -> button.setEnabled(false));
            speed.setEnabled(false);
            return;
        }

        gameLabel.setText(gameId + "  ·  " + state);
        gameLabel.setForeground(Theme.text());
        speed.setEnabled(true);

        boolean created = "CREATED".equals(state);
        boolean running = "RUNNING".equals(state);
        boolean paused = "PAUSED".equals(state);
        boolean terminal = "FINISHED".equals(state) || "ABORTED".equals(state);

        buttons.get(Command.START_GAME).setEnabled(created);
        buttons.get(Command.PAUSE_GAME).setEnabled(running);
        buttons.get(Command.RESUME_GAME).setEnabled(paused);
        buttons.get(Command.STEP_ROUND).setEnabled(created || paused);
        buttons.get(Command.ABORT_GAME).setEnabled(!terminal);
    }

    /** Reflects a speed set by another client without firing a request back. */
    public void showSpeed(long tickMillis) {
        int clamped = (int) Math.max(MIN_TICK_MILLIS, Math.min(MAX_TICK_MILLIS, tickMillis));
        if (speed.getValue() != clamped && !speed.getValueIsAdjusting()) {
            speed.setValue(clamped);
        }
    }

    public void applyTheme() {
        setBackground(Theme.panel());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()));
        speed.setBackground(Theme.panel());
        updateSpeedLabel();
        for (JButton button : buttons.values()) {
            button.setBackground(Theme.panelAlt());
            button.setForeground(Theme.text());
        }
        repaint();
    }
}
