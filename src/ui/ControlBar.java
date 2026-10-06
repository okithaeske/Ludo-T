package ui;

import shared.Command;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Start / Pause / Resume / Step / Abort.
 *
 * <p>Buttons are enabled from the selected game's state rather than from what this client last
 * clicked, because another client may have paused the game a moment ago. Deriving the controls
 * from server state is what stops two clients disagreeing about what is possible.
 *
 * <p>There is deliberately no speed control: every game this window creates runs at one
 * default round delay. The server still understands {@code SET_SPEED}, which the console
 * client and the load harness use.
 */
public final class ControlBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Map<Command, JButton> buttons = new LinkedHashMap<>();
    private final JLabel gameLabel = new JLabel("No game selected");

    private Consumer<Command> commandListener = command -> { };
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

        setSelectedGame(null, null, false);
        applyTheme();
    }

    private void addButton(Command command, String text, String tooltip) {
        JButton button = new ThemedButton(text);
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

    /**
     * Points the bar at a game and enables only the commands its state allows.
     *
     * @param state one of the {@code SessionState} names, or null when nothing is selected
     * @param controllable false for another player's game, which leaves every control disabled.
     *                     The server refuses those commands regardless; this only stops the
     *                     window offering something that cannot work.
     */
    public void setSelectedGame(String gameId, String state, boolean controllable) {
        this.gameId = gameId;

        if (gameId == null) {
            gameLabel.setText("No game selected");
            gameLabel.setForeground(Theme.textMuted());
            buttons.values().forEach(button -> button.setEnabled(false));
            return;
        }

        gameLabel.setForeground(Theme.text());
        if (!controllable) {
            gameLabel.setText(gameId + "  ·  " + state + "  ·  watching only");
            buttons.values().forEach(button -> button.setEnabled(false));
            return;
        }
        gameLabel.setText(gameId + "  ·  " + state);

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

    public void applyTheme() {
        setBackground(Theme.panel());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()));
        // The label's colour is otherwise only set when the selection changes, which left it in
        // the previous theme's colour after a switch.
        gameLabel.setForeground(gameId == null ? Theme.textMuted() : Theme.text());
        repaint();
    }
}
