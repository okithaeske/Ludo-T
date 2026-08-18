package ui;

import shared.SessionSummaryDto;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The list of games the server is hosting.
 *
 * <p>Rows arrive from server pushes, never from polling, so a game another client creates
 * appears here on its own. Refreshing therefore has to preserve the user's selection — a naive
 * "clear and refill" would deselect the game they are watching several times a second, which
 * is the difference between a live table and an unusable one.
 */
public final class LobbyPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SessionTableModel model = new SessionTableModel();
    private final JTable table = new JTable(model);
    private final JLabel heading = new JLabel("Games");
    private Consumer<String> selectionListener = gameId -> { };

    private static final class SessionTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS =
                {"Game", "Mode", "Seed", "State", "Round", "Viewers"};

        private final transient List<SessionSummaryDto> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            // Integer columns so the sorter orders 10 after 9 rather than before it.
            return switch (column) {
                case 4, 5 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            SessionSummaryDto session = rows.get(row);
            return switch (column) {
                case 0 -> session.gameId();
                case 1 -> session.mode();
                case 2 -> session.isSeeded() ? String.valueOf(session.seed()) : "—";
                case 3 -> session.state();
                case 4 -> session.round();
                case 5 -> session.subscribers();
                default -> "";
            };
        }

        SessionSummaryDto rowAt(int index) {
            return rows.get(index);
        }

        void replaceAll(List<SessionSummaryDto> sessions) {
            rows.clear();
            rows.addAll(sessions);
            fireTableDataChanged();
        }

        /** Inserts or updates one session, leaving every other row untouched. */
        void upsert(SessionSummaryDto session) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).gameId().equals(session.gameId())) {
                    rows.set(i, session);
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
            rows.add(0, session);
            fireTableRowsInserted(0, 0);
        }

        int indexOf(String gameId) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).gameId().equals(gameId)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public LobbyPanel() {
        setLayout(new BorderLayout(0, 6));
        setBackground(Theme.panel());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        heading.setFont(Theme.uiFontBold(13));
        heading.setForeground(Theme.text());
        add(heading, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowSorter(new TableRowSorter<>(model));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new StateColourRenderer());
        table.setDefaultRenderer(Integer.class, new StateColourRenderer());

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            String gameId = getSelectedGameId();
            if (gameId != null) {
                selectionListener.accept(gameId);
            }
        });

        sizeColumns();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border()));
        add(scroll, BorderLayout.CENTER);

        applyTheme();
    }

    /** Colours the state column so RUNNING / PAUSED / FINISHED are readable at a glance. */
    private static final class StateColourRenderer extends DefaultTableCellRenderer {

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
                component.setForeground(Theme.text());
            }

            if (column == 3 && value != null && !selected) {
                component.setForeground(switch (value.toString()) {
                    case "RUNNING" -> Theme.ok();
                    case "PAUSED" -> Theme.warn();
                    case "ABORTED" -> Theme.danger();
                    case "FINISHED" -> Theme.accent();
                    default -> Theme.textMuted();
                });
            }
            return component;
        }
    }

    /**
     * Widths chosen per column. Left to itself the table splits the space evenly, which
     * truncates "RUNNING" to "RUN..." — and the state column is the one the operator reads
     * most.
     */
    private void sizeColumns() {
        int[] widths = {46, 66, 52, 74, 52, 44};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    public void setSelectionListener(Consumer<String> listener) {
        this.selectionListener = listener;
    }

    public String getSelectedGameId() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return model.rowAt(table.convertRowIndexToModel(viewRow)).gameId();
    }

    /** Replaces every row, used for the initial load. */
    public void setSessions(List<SessionSummaryDto> sessions) {
        String selected = getSelectedGameId();
        model.replaceAll(sessions);
        reselect(selected);
        updateHeading();
    }

    /** Applies a single change from a push. */
    public void upsertSession(SessionSummaryDto session) {
        String selected = getSelectedGameId();
        model.upsert(session);
        reselect(selected);
        updateHeading();
    }

    private void reselect(String gameId) {
        if (gameId == null) {
            return;
        }
        int modelRow = model.indexOf(gameId);
        if (modelRow < 0) {
            return;
        }
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
    }

    /** Selects a game programmatically, for instance one this client just created. */
    public void select(String gameId) {
        reselect(gameId);
    }

    private void updateHeading() {
        heading.setText("Games (" + model.getRowCount() + ")");
    }

    public void applyTheme() {
        setBackground(Theme.panel());
        heading.setForeground(Theme.text());
        table.setBackground(Theme.panel());
        table.setForeground(Theme.text());
        table.setGridColor(Theme.border());
        table.setSelectionBackground(Theme.accent());
        table.setSelectionForeground(Theme.panel());
        table.getTableHeader().setBackground(Theme.panelAlt());
        table.getTableHeader().setForeground(Theme.text());
        table.getTableHeader().setFont(Theme.uiFontBold(12));
        repaint();
    }
}
