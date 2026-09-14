package ui;

import shared.FinishedGameDto;
import shared.StrategyRankingDto;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * What the database tier remembers: finished games, and how each AI strategy has done.
 *
 * <h2>Why this panel exists</h2>
 * Without it the database tier would be write-only, and a demonstration could not show that
 * anything was actually stored. Everything else in this GUI reads a live session; these two
 * tables read games whose sessions no longer exist, which is the only visible evidence that
 * the third tier does something the other two cannot.
 *
 * <h2>Why it polls instead of receiving pushes</h2>
 * Every other panel here is push-driven, and deliberately so. This one is not: the server
 * broadcasts session events, not database writes, and adding a "row stored" push would put the
 * database on the notification path of every game — coupling the two tiers exactly where the
 * repository's queue was designed to decouple them. History changes when a game ends, which is
 * rare, so a refresh on demand and on tab selection costs one query and keeps the tiers apart.
 *
 * <h2>Threading</h2>
 * Every method here must be called on the Event Dispatch Thread, like all Swing. The two
 * {@code show*} methods are the hand-off points: {@code MainFrame} completes the request on a
 * client thread and marshals the result here through {@code SwingUtilities.invokeLater}.
 */
public final class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ResultTableModel results = new ResultTableModel();
    private final RankingTableModel rankings = new RankingTableModel();
    private final JTable resultTable = new JTable(results);
    private final JTable rankingTable = new JTable(rankings);
    private final JLabel status = new JLabel("Not loaded");
    private final JButton refresh = new JButton("Refresh");

    private final JLabel resultsHeading = new JLabel("Finished games");
    private final JLabel rankingsHeading = new JLabel("Strategy leaderboard");

    public HistoryPanel() {
        setLayout(new BorderLayout(0, 6));
        setBackground(Theme.panel());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildSection(resultsHeading, resultTable, results),
                buildSection(rankingsHeading, rankingTable, rankings));
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        applyTheme();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);
        refresh.setFont(Theme.uiFont(12));
        refresh.setFocusable(false);
        refresh.setToolTipText("Re-read the database tier");
        status.setFont(Theme.uiFont(11));
        header.add(refresh);
        header.add(status);
        return header;
    }

    private JPanel buildSection(JLabel heading, JTable table, AbstractTableModel model) {
        heading.setFont(Theme.uiFontBold(13));

        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(new TableRowSorter<>(model));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new HistoryRenderer());
        table.setDefaultRenderer(Integer.class, new HistoryRenderer());
        table.setDefaultRenderer(Double.class, new HistoryRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border()));

        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        section.add(heading, BorderLayout.NORTH);
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    /** Runs when the user asks for a reload; {@code MainFrame} supplies the query. */
    public void setRefreshAction(Runnable action) {
        for (java.awt.event.ActionListener listener : refresh.getActionListeners()) {
            refresh.removeActionListener(listener);
        }
        refresh.addActionListener(event -> action.run());
    }

    public void showResults(List<FinishedGameDto> rows) {
        results.replaceAll(rows);
        resultsHeading.setText("Finished games (" + rows.size() + ")");
        sizeResultColumns();
    }

    public void showRankings(List<StrategyRankingDto> rows) {
        rankings.replaceAll(rows);
        rankingsHeading.setText("Strategy leaderboard (" + rows.size() + ")");
        sizeRankingColumns();
    }

    /** Says plainly why the tables are empty, so "no database" never looks like "no games". */
    public void showStatus(String message) {
        status.setText(message);
        status.setForeground(Theme.textMuted());
    }

    public void showProblem(String message) {
        status.setText(message);
        status.setForeground(Theme.danger());
    }

    // ── Table models ─────────────────────────────────────────────────────────

    private static final class ResultTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS =
                {"Game", "Mode", "Rounds", "Winner", "Strategy", "Order", "Finished"};

        private final transient List<FinishedGameDto> rows = new ArrayList<>();

        // Not static: SimpleDateFormat is not thread-safe, but every use here is on the
        // Event Dispatch Thread, so one instance per model is both safe and cheap.
        private final transient SimpleDateFormat stamp = new SimpleDateFormat("dd MMM HH:mm");

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 2 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            FinishedGameDto game = rows.get(row);
            return switch (column) {
                case 0 -> game.gameId();
                case 1 -> game.mode();
                case 2 -> game.rounds();
                case 3 -> game.hasWinner() ? game.winnerColour() : "—";
                case 4 -> game.winnerStrategy() == null ? "—" : game.winnerStrategy();
                case 5 -> String.join(" > ", game.finishingOrder());
                case 6 -> game.finishedAtMillis() == 0L
                        ? "—" : stamp.format(new Date(game.finishedAtMillis()));
                default -> "";
            };
        }

        void replaceAll(List<FinishedGameDto> games) {
            rows.clear();
            rows.addAll(games);
            fireTableDataChanged();
        }
    }

    private static final class RankingTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS =
                {"Strategy", "Played", "Wins", "Win rate", "Captures", "Avg home"};

        private final transient List<StrategyRankingDto> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 1, 2, 4 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            StrategyRankingDto ranking = rows.get(row);
            return switch (column) {
                case 0 -> ranking.strategy();
                case 1 -> ranking.gamesPlayed();
                case 2 -> ranking.wins();
                case 3 -> String.format("%.0f%%", ranking.winRate() * 100);
                case 4 -> ranking.totalCaptures();
                case 5 -> String.format("%.2f", ranking.averagePiecesHome());
                default -> "";
            };
        }

        void replaceAll(List<StrategyRankingDto> rankings) {
            rows.clear();
            rows.addAll(rankings);
            fireTableDataChanged();
        }
    }

    /** Paints the winner column in that player's colour, matching the board. */
    private static final class HistoryRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            component.setFont(Theme.uiFont(12));
            if (!selected) {
                component.setBackground(Theme.panel());
                boolean isWinnerColumn = column == 3
                        && "Winner".equals(table.getColumnName(column));
                component.setForeground(isWinnerColumn && value != null
                        ? Theme.forColour(value.toString())
                        : Theme.text());
            }
            return component;
        }
    }

    private void sizeResultColumns() {
        applyWidths(resultTable, new int[] {52, 62, 54, 62, 128, 132, 84});
    }

    private void sizeRankingColumns() {
        applyWidths(rankingTable, new int[] {150, 54, 48, 62, 66, 66});
    }

    private static void applyWidths(JTable table, int[] widths) {
        for (int column = 0; column < widths.length
                && column < table.getColumnModel().getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    public void applyTheme() {
        setBackground(Theme.panel());
        resultsHeading.setForeground(Theme.text());
        rankingsHeading.setForeground(Theme.text());
        status.setForeground(Theme.textMuted());
        for (JTable table : new JTable[] {resultTable, rankingTable}) {
            table.setBackground(Theme.panel());
            table.setForeground(Theme.text());
            table.setGridColor(Theme.border());
            table.setSelectionBackground(Theme.accent());
            table.setSelectionForeground(Theme.panel());
            table.getTableHeader().setBackground(Theme.panelAlt());
            table.getTableHeader().setForeground(Theme.text());
            table.getTableHeader().setFont(Theme.uiFontBold(12));
        }
        repaint();
    }
}
