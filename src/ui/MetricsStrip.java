package ui;

import shared.ServerMetricsDto;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Live server load, pushed once a second.
 *
 * <p>Exists so the request queue is visible rather than merely described: run the load clients
 * and the bar fills, the worker count rises, and the saturation counter starts climbing. That
 * is far more convincing evidence of a working queue than a number in a report.
 */
public final class MetricsStrip extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel connection = new JLabel("Disconnected");
    private final JLabel clients = new JLabel();
    private final JLabel games = new JLabel();
    private final JLabel throughput = new JLabel();
    private final JLabel saturation = new JLabel();
    private final QueueBar queueBar = new QueueBar();

    /** A small horizontal gauge of queue depth against capacity. */
    private static final class QueueBar extends JPanel {

        private static final long serialVersionUID = 1L;

        private double load;
        private int depth;
        private int capacity = 1;

        QueueBar() {
            setPreferredSize(new Dimension(120, 14));
            setOpaque(false);
            setToolTipText("Requests waiting for a worker");
        }

        void update(ServerMetricsDto metrics) {
            this.load = Math.max(0, Math.min(1, metrics.queueLoad()));
            this.depth = metrics.queueDepth();
            this.capacity = Math.max(1, metrics.queueCapacity());
            setToolTipText("Queue " + depth + " / " + capacity + " waiting");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g.setColor(Theme.panelAlt());
            g.fillRoundRect(0, 0, width, height, height, height);

            if (load > 0) {
                // Green while there is headroom, amber as it fills, red when nearly full —
                // the same reading the tutor needs during the load demonstration.
                Color fill = load > 0.75 ? Theme.danger()
                        : load > 0.35 ? Theme.warn() : Theme.ok();
                g.setColor(fill);
                g.fillRoundRect(0, 0, (int) Math.max(height, width * load), height,
                        height, height);
            }

            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0, 0, width - 1, height - 1, height, height);
            g.dispose();
        }
    }

    public MetricsStrip() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        setBackground(Theme.panelAlt());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        left.setOpaque(false);
        left.add(connection);
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        right.setOpaque(false);
        right.add(label("Queue"));
        right.add(queueBar);
        right.add(saturation);
        right.add(throughput);
        right.add(games);
        right.add(clients);
        add(right, BorderLayout.EAST);

        for (JLabel field : new JLabel[] {connection, clients, games, throughput, saturation}) {
            field.setFont(Theme.monoFont(11));
        }
        showDisconnected();
        applyTheme();
    }

    private JLabel label(String text) {
        JLabel field = new JLabel(text);
        field.setFont(Theme.uiFont(11));
        field.setForeground(Theme.textMuted());
        return field;
    }

    public void showConnected(String host, int port) {
        connection.setText("● connected  " + host + ":" + port);
        connection.setForeground(Theme.ok());
    }

    public void showDisconnected() {
        connection.setText("● disconnected");
        connection.setForeground(Theme.danger());
        clients.setText("");
        games.setText("");
        throughput.setText("");
        saturation.setText("");
    }

    public void showMetrics(ServerMetricsDto metrics) {
        queueBar.update(metrics);
        clients.setText(metrics.connectedClients() + " client"
                + (metrics.connectedClients() == 1 ? "" : "s"));
        games.setText(metrics.liveSessions() + " game"
                + (metrics.liveSessions() == 1 ? "" : "s"));
        throughput.setText(metrics.completedRequests() + " done");
        throughput.setToolTipText(metrics.inFlight() + " in flight, "
                + metrics.activeWorkers() + "/" + metrics.poolSize() + " workers busy");

        saturation.setText("sat " + metrics.rejectedRequests());
        saturation.setToolTipText(
                "Times the queue filled and a connection thread ran the work itself");
        saturation.setForeground(metrics.rejectedRequests() > 0 ? Theme.warn() : Theme.textMuted());

        clients.setForeground(Theme.textMuted());
        games.setForeground(Theme.textMuted());
        throughput.setForeground(Theme.textMuted());
    }

    public void applyTheme() {
        setBackground(Theme.panelAlt());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        repaint();
    }
}
