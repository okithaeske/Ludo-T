package ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.util.List;

/**
 * Draws a table's column headings from {@link Theme}.
 *
 * <p>The Windows look and feel paints its own white header and ignores the header's
 * background, while still applying its foreground — so the dark theme's near-white heading
 * text landed on white and the column names all but disappeared. Replacing the header's
 * renderer is the only way to control both colours together.
 *
 * <p>The native renderer also drew the sort arrow, so that is reproduced here as text.
 */
final class ThemedHeaderRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean selected, boolean focused,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, value, false, false, row, column);
        label.setText(String.valueOf(value) + sortMark(table, column));
        label.setOpaque(true);
        label.setBackground(Theme.panelAlt());
        label.setForeground(Theme.text());
        label.setFont(Theme.uiFontBold(12));
        label.setHorizontalAlignment(JLabel.LEFT);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.border()),
                BorderFactory.createEmptyBorder(4, 6, 4, 4)));
        return label;
    }

    private static String sortMark(JTable table, int viewColumn) {
        RowSorter<?> sorter = table.getRowSorter();
        if (sorter == null) {
            return "";
        }
        List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
        if (keys.isEmpty()
                || keys.get(0).getColumn() != table.convertColumnIndexToModel(viewColumn)) {
            return "";
        }
        SortOrder order = keys.get(0).getSortOrder();
        return order == SortOrder.ASCENDING ? "  ^" : order == SortOrder.DESCENDING ? "  v" : "";
    }
}
