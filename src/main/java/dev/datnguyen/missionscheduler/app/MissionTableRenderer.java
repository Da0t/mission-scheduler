package dev.datnguyen.missionscheduler.app;

import dev.datnguyen.missionscheduler.planning.ScheduledTask;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

final class MissionTableRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    private static final Color EVEN_ROW = new Color(250, 251, 253);
    private static final Color ODD_ROW = Color.WHITE;
    private static final Color CRITICAL_ROW = new Color(255, 245, 224);
    private static final Color RUNNING_ROW = new Color(224, 239, 255);
    private static final Color COMPLETE_ROW = new Color(228, 247, 235);
    private static final Color TEXT = new Color(28, 37, 54);

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean selected,
            boolean focused,
            int row,
            int column) {
        super.getTableCellRendererComponent(table, value, selected, focused, row, column);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        setHorizontalAlignment(column >= 1 && column <= 5 ? SwingConstants.CENTER : SwingConstants.LEFT);

        if (!selected) {
            setForeground(TEXT);
            MissionTableModel model = (MissionTableModel) table.getModel();
            int modelRow = table.convertRowIndexToModel(row);
            ScheduledTask task = model.taskAt(modelRow);
            MissionTableModel.TaskState state = model.stateAt(modelRow);
            setBackground(switch (state) {
                case RUNNING -> RUNNING_ROW;
                case COMPLETE -> COMPLETE_ROW;
                case PENDING -> task.critical()
                        ? CRITICAL_ROW
                        : (row % 2 == 0 ? EVEN_ROW : ODD_ROW);
            });
        }
        return this;
    }
}
