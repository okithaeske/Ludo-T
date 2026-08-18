package client;

import ui.MainFrame;
import ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;

/**
 * The client application: one of the three processes in the deployed system.
 *
 * <p>Run with {@code java -cp out client.ClientMain [host] [port]}, or via
 * {@code run-client.ps1}. Start two against one server to see a change made in one appear in
 * the other.
 */
public final class ClientMain {

    private static final int DEFAULT_PORT = 5599;

    private ClientMain() {
        // Entry point only.
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? parsePort(args[1]) : DEFAULT_PORT;

        MainFrame.installLookAndFeel();
        Theme.setDark(true);

        // Connecting blocks on a socket, so it happens here on the main thread — before the
        // EDT has anything to paint — rather than inside the window it is about to open.
        ServerConnection connection;
        try {
            connection = ServerConnection.connect(host, port);
        } catch (IOException e) {
            reportFailure(host, port, e);
            return;
        }

        SwingUtilities.invokeLater(() -> new MainFrame(connection).setVisible(true));
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("[client] '" + raw + "' is not a port; using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static void reportFailure(String host, int port, IOException cause) {
        String message = "Could not reach the Ludo-T server at " + host + ":" + port
                + "\n\n" + cause.getMessage()
                + "\n\nStart it with:  run-server.ps1";
        System.out.println("[client] " + message.replace("\n", " "));
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Cannot connect",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        });
    }
}
