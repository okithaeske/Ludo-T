package ui;

import client.ServerConnection;
import shared.BoardSnapshot;
import shared.Command;
import shared.Response;
import shared.ServerEvent;
import shared.ServerMetricsDto;
import shared.SessionSummaryDto;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The control room: lobby, board, controls, player status, event log and server load.
 *
 * <h2>What this window is for</h2>
 * Nobody plays the pieces — the four AI strategies drive every game, exactly as in Assignment
 * 1. This is an operator's view: create games, run and pause and step them, and watch several
 * proceed at once. Every button is a request to the server, and every change on screen comes
 * from a server push, including changes another client caused.
 *
 * <h2>Threading</h2>
 * Everything in this class runs on the EDT. {@link ServerConnection} marshals pushes here
 * before delivering them, and replies to requests are handled with
 * {@code thenAcceptAsync(..., SwingUtilities::invokeLater)} — never a plain {@code thenAccept},
 * which would run on the receiver thread and touch Swing components off the EDT.
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final int RECONNECT_DELAY_MILLIS = 3_000;
    /**
     * Width of the lobby and the players/log column.
     *
     * <p>Deliberately modest. Windows commonly runs at 125% scaling, where a 1536-pixel
     * screen offers only about 1229 logical pixels — so two 320-wide side panels plus a board
     * simply do not fit, and the right-hand column silently falls off the edge. These figures
     * keep the whole layout inside roughly 980 logical pixels.
     */
    private static final int SIDE_PANEL_WIDTH = 260;

    private final String host;
    private final int port;

    private final LobbyPanel lobby = new LobbyPanel();
    private final BoardCanvas board = new BoardCanvas();
    private final ControlBar controls = new ControlBar();
    private final PlayerStatusPanel players = new PlayerStatusPanel();
    private final EventLogPanel eventLog = new EventLogPanel();
    private final MetricsStrip metrics = new MetricsStrip();
    private final JLabel toast = new JLabel("", JLabel.CENTER);
    private final Timer toastTimer;
    private final Timer reconnectTimer;

    /** Latest known state per game, so the control bar can be driven without a round trip. */
    private final Map<String, SessionSummaryDto> knownSessions = new HashMap<>();

    private JSplitPane rightSplit;

    private ServerConnection connection;
    private String selectedGameId;

    public MainFrame(ServerConnection connection) {
        super("Ludo-T Control Room");
        this.connection = connection;
        this.host = connection.getHost();
        this.port = connection.getPort();

        this.toastTimer = new Timer(2600, event -> toast.setVisible(false));
        this.toastTimer.setRepeats(false);
        this.reconnectTimer = new Timer(RECONNECT_DELAY_MILLIS, event -> attemptReconnect());
        this.reconnectTimer.setRepeats(false);

        buildLayout();
        installShortcuts();
        wireControls();
        attachConnection(connection);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }

            @Override
            public void windowOpened(WindowEvent event) {
                reportLayout();
            }

        });

        setMinimumSize(new Dimension(940, 600));
        // Sized against the logical desktop rather than a fixed guess, so the window fits
        // whatever scaling the demo machine or projector happens to use.
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1180, screen.width - 60), Math.min(700, screen.height - 60));
        setLocationRelativeTo(null);
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.background());

        root.add(metrics, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(buildLobbyHeader(), BorderLayout.NORTH);
        left.add(lobby, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(SIDE_PANEL_WIDTH, 0));
        left.setMinimumSize(new Dimension(240, 0));

        JPanel centre = new JPanel(new BorderLayout());
        centre.setOpaque(false);
        centre.add(board, BorderLayout.CENTER);
        centre.add(controls, BorderLayout.SOUTH);
        // Without a minimum the board would be squeezed to nothing when a divider is dragged.
        centre.setMinimumSize(new Dimension(420, 0));

        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, players, eventLog);
        rightSplit.setResizeWeight(0.35);
        rightSplit.setBorder(null);
        rightSplit.setPreferredSize(new Dimension(SIDE_PANEL_WIDTH, 0));
        rightSplit.setMinimumSize(new Dimension(240, 0));

        // BorderLayout rather than nested JSplitPanes for the horizontal arrangement.
        // setDividerLocation is only honoured once a split pane has been validated, so on a
        // freshly opened window the calls were silently dropped and the board took the entire
        // width with the side panels collapsed to zero. WEST and EAST always get exactly their
        // preferred width, which is the behaviour wanted here anyway; the board simply takes
        // whatever is left. The one split pane kept is the vertical one inside EAST, where its
        // own preferred sizes are enough and no explicit divider placement is needed.
        root.add(left, BorderLayout.WEST);
        root.add(rightSplit, BorderLayout.EAST);
        root.add(centre, BorderLayout.CENTER);

        setContentPane(root);
        buildToast();
    }

    /**
     * Prints the widths the layout actually produced.
     *
     * <p>Kept because side panels being silently squeezed off a scaled display is invisible
     * in code review and obvious in one line of output.
     */
    private void reportLayout() {
        SwingUtilities.invokeLater(() -> System.out.println(
                "[ui] frame=" + getContentPane().getWidth()
                        + " lobby=" + lobby.getParent().getWidth()
                        + " board=" + board.getWidth()
                        + " side=" + rightSplit.getWidth()
                        + " screen=" + java.awt.Toolkit.getDefaultToolkit().getScreenSize().width
                        + " scale=" + getGraphicsConfiguration().getDefaultTransform().getScaleX()));
    }

    private JPanel buildLobbyHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        header.setBackground(Theme.panel());

        JButton newGame = new JButton("New game");
        newGame.setFont(Theme.uiFont(12));
        newGame.setToolTipText("Create a game on the server  (Ctrl+N)");
        newGame.setFocusable(false);
        newGame.addActionListener(event -> showNewGameDialog());
        header.add(newGame);

        JButton refresh = new JButton("Refresh");
        refresh.setFont(Theme.uiFont(12));
        refresh.setToolTipText("Re-fetch the game list  (F5)");
        refresh.setFocusable(false);
        refresh.addActionListener(event -> refreshLobby());
        header.add(refresh);

        JButton theme = new JButton("Theme");
        theme.setFont(Theme.uiFont(12));
        theme.setToolTipText("Switch between dark and light  (Ctrl+D)");
        theme.setFocusable(false);
        theme.addActionListener(event -> toggleTheme());
        header.add(theme);

        return header;
    }

    private void buildToast() {
        toast.setOpaque(true);
        toast.setVisible(false);
        toast.setFont(Theme.uiFont(12));
        toast.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        // On the layered pane so a message never pushes the layout around.
        getLayeredPane().add(toast, JLayeredPaneDepth.TOAST);
    }

    /** Keeps the magic number for the toast layer in one named place. */
    private static final class JLayeredPaneDepth {
        static final Integer TOAST = Integer.valueOf(300);

        private JLayeredPaneDepth() {
        }
    }

    private void showToast(String message, Color colour) {
        toast.setText(message);
        toast.setBackground(colour);
        toast.setForeground(Theme.isDark() ? Color.WHITE : Color.WHITE);

        Dimension size = toast.getPreferredSize();
        toast.setBounds(getWidth() - size.width - 40, getHeight() - size.height - 70,
                size.width, size.height);
        toast.setVisible(true);
        toastTimer.restart();
    }

    // ── Shortcuts ────────────────────────────────────────────────────────────

    private void installShortcuts() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                "newGame", this::showNewGameDialog);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                "refresh", this::refreshLobby);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
                "theme", this::toggleTheme);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK),
                "start", () -> sendForSelected(Command.START_GAME));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, InputEvent.CTRL_DOWN_MASK),
                "step", () -> sendForSelected(Command.STEP_ROUND));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK),
                "pauseResume", this::togglePauseResume);
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        JComponent root = (JComponent) getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, new javax.swing.AbstractAction() {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    /** One key for pause and resume, chosen from the game's current state. */
    private void togglePauseResume() {
        SessionSummaryDto session = knownSessions.get(selectedGameId);
        if (session == null) {
            return;
        }
        if ("RUNNING".equals(session.state())) {
            sendForSelected(Command.PAUSE_GAME);
        } else if ("PAUSED".equals(session.state())) {
            sendForSelected(Command.RESUME_GAME);
        }
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    private void wireControls() {
        lobby.setSelectionListener(this::selectGame);
        controls.setCommandListener(this::sendForSelected);
        controls.setSpeedListener((gameId, millis) ->
                send(Command.SET_SPEED,
                        Map.of("gameId", gameId, "tickMillis", String.valueOf(millis))));
    }

    private void attachConnection(ServerConnection connection) {
        connection.addEventListener(this::onServerEvent);
        connection.addDisconnectListener(this::onDisconnected);
        metrics.showConnected(host, port);
        refreshLobby();
    }

    // ── Server events (all arrive on the EDT) ────────────────────────────────

    private void onServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case GAME_CREATED, GAME_STATE_CHANGED -> onSessionChanged(event);
            case SNAPSHOT, GAME_OVER -> onSnapshot(event);
            case LOG -> onLogLine(event);
            case METRICS -> metrics.showMetrics((ServerMetricsDto) event.getPayload());
        }
    }

    private void onSessionChanged(ServerEvent event) {
        SessionSummaryDto session = (SessionSummaryDto) event.getPayload();
        knownSessions.put(session.gameId(), session);
        lobby.upsertSession(session);

        if (session.gameId().equals(selectedGameId)) {
            controls.setSelectedGame(session.gameId(), session.state());
            controls.showSpeed(session.tickMillis());
        }
    }

    private void onSnapshot(ServerEvent event) {
        BoardSnapshot snapshot = (BoardSnapshot) event.getPayload();
        // Snapshots for games this client is not looking at are ignored rather than queued,
        // so switching games is instant and watching one game costs nothing for the others.
        if (!snapshot.gameId().equals(selectedGameId)) {
            return;
        }
        board.showSnapshot(snapshot);
        players.showSnapshot(snapshot);

        if (snapshot.gameOver()) {
            showToast("Game " + snapshot.gameId() + " finished — "
                    + String.join(", ", snapshot.finishingOrder()), Theme.accent());
        }
    }

    private void onLogLine(ServerEvent event) {
        if (event.getGameId() != null && event.getGameId().equals(selectedGameId)) {
            eventLog.append(String.valueOf(event.getPayload()));
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void selectGame(String gameId) {
        if (gameId == null || gameId.equals(selectedGameId)) {
            return;
        }
        String previous = selectedGameId;
        selectedGameId = gameId;

        // Unsubscribe from the old game so the server stops sending boards nobody is watching.
        if (previous != null) {
            send(Command.UNSUBSCRIBE, Map.of("gameId", previous));
        }
        send(Command.SUBSCRIBE, Map.of("gameId", gameId));

        eventLog.clear();
        eventLog.appendSystem("Watching " + gameId);
        board.clear();

        SessionSummaryDto known = knownSessions.get(gameId);
        controls.setSelectedGame(gameId, known == null ? "?" : known.state());
        if (known != null) {
            controls.showSpeed(known.tickMillis());
        }

        // Fetch the board immediately rather than waiting for the next round to be pushed —
        // a paused or finished game would otherwise show nothing at all.
        send(Command.GET_SNAPSHOT, Map.of("gameId", gameId))
                .thenAcceptAsync(response -> {
                    if (response.isOk() && response.getPayload() instanceof BoardSnapshot board2
                            && board2.gameId().equals(selectedGameId)) {
                        board.showSnapshot(board2);
                        players.showSnapshot(board2);
                    }
                }, SwingUtilities::invokeLater);
    }

    private void sendForSelected(Command command) {
        if (selectedGameId == null) {
            showToast("Select a game first", Theme.warn());
            return;
        }
        send(command, Map.of("gameId", selectedGameId));
    }

    private CompletableFuture<Response> send(Command command, Map<String, String> params) {
        CompletableFuture<Response> future = connection.send(command, params);
        future.thenAcceptAsync(response -> {
            if (!response.isOk()) {
                showToast(response.getMessage(), Theme.danger());
            }
        }, SwingUtilities::invokeLater).exceptionally(error -> {
            SwingUtilities.invokeLater(() ->
                    showToast("Request failed: " + rootMessage(error), Theme.danger()));
            return null;
        });
        return future;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void refreshLobby() {
        connection.send(Command.LIST_GAMES)
                .thenAcceptAsync(this::applyLobbyResponse, SwingUtilities::invokeLater)
                .exceptionally(error -> null);
    }

    @SuppressWarnings("unchecked")
    private void applyLobbyResponse(Response response) {
        if (!response.isOk() || !(response.getPayload() instanceof List<?> payload)) {
            return;
        }
        List<SessionSummaryDto> sessions = (List<SessionSummaryDto>) payload;
        knownSessions.clear();
        sessions.forEach(session -> knownSessions.put(session.gameId(), session));
        lobby.setSessions(sessions);
    }

    private void showNewGameDialog() {
        JComboBox<String> mode = new JComboBox<>(new String[] {"LUDO_T", "CLASSIC"});
        JTextField seed = new JTextField();
        JTextField tick = new JTextField("250");

        seed.setToolTipText("Leave blank for a random game; any number makes it reproducible");
        tick.setToolTipText("Milliseconds between rounds; 0 runs as fast as possible");

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Mode"));
        form.add(mode);
        form.add(new JLabel("Seed (optional)"));
        form.add(seed);
        form.add(new JLabel("Round delay (ms)"));
        form.add(tick);

        int choice = JOptionPane.showConfirmDialog(this, form, "New game",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("mode", String.valueOf(mode.getSelectedItem()));
        params.put("tickMillis", tick.getText().isBlank() ? "250" : tick.getText().trim());
        if (!seed.getText().isBlank()) {
            params.put("seed", seed.getText().trim());
        }

        send(Command.CREATE_GAME, params).thenAcceptAsync(response -> {
            if (response.isOk() && response.getPayload() instanceof SessionSummaryDto summary) {
                knownSessions.put(summary.gameId(), summary);
                lobby.upsertSession(summary);
                lobby.select(summary.gameId());
                selectGame(summary.gameId());
                showToast("Created " + summary.gameId(), Theme.ok());
            }
        }, SwingUtilities::invokeLater);
    }

    private void toggleTheme() {
        Theme.toggle();
        applyTheme();
    }

    private void applyTheme() {
        getContentPane().setBackground(Theme.background());
        lobby.applyTheme();
        controls.applyTheme();
        players.applyTheme();
        eventLog.applyTheme();
        metrics.applyTheme();
        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    // ── Connection loss and recovery ─────────────────────────────────────────

    private void onDisconnected() {
        metrics.showDisconnected();
        eventLog.appendSystem("Connection lost — retrying every "
                + (RECONNECT_DELAY_MILLIS / 1000) + "s");
        showToast("Disconnected from the server", Theme.danger());
        reconnectTimer.restart();
    }

    private void attemptReconnect() {
        // Connecting blocks, so it must not happen on the EDT.
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return ServerConnection.connect(host, port);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .thenAcceptAsync(reconnected -> {
                    if (reconnected == null) {
                        reconnectTimer.restart();
                        return;
                    }
                    connection = reconnected;
                    attachConnection(reconnected);
                    eventLog.appendSystem("Reconnected.");
                    showToast("Reconnected", Theme.ok());

                    // Re-subscribe, or the board would sit frozen on a live game.
                    if (selectedGameId != null) {
                        String gameId = selectedGameId;
                        selectedGameId = null;
                        selectGame(gameId);
                    }
                }, SwingUtilities::invokeLater);
    }

    private void shutdown() {
        reconnectTimer.stop();
        toastTimer.stop();
        connection.close();
        dispose();
        System.exit(0);
    }

    /** Applies a native-looking look and feel before any component is created. */
    public static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // The cross-platform default is perfectly usable; carry on.
        }
    }
}
