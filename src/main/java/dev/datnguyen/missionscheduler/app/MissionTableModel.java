package dev.datnguyen.missionscheduler.app;

import dev.datnguyen.missionscheduler.planning.MissionPlan;
import dev.datnguyen.missionscheduler.planning.ScheduledTask;
import dev.datnguyen.missionscheduler.simulation.SimulationEvent;

import javax.swing.table.AbstractTableModel;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("serial") // Swing models are not persisted by this application.
public final class MissionTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {
            "Task", "Start", "Finish", "Duration", "Slack", "Critical", "State"
    };

    private List<ScheduledTask> tasks = List.of();
    private final Map<String, Integer> rowByTask = new HashMap<>();
    private final Map<String, TaskState> states = new HashMap<>();

    public void setPlan(MissionPlan plan) {
        tasks = plan.tasks();
        rowByTask.clear();
        states.clear();
        for (int row = 0; row < tasks.size(); row++) {
            String id = tasks.get(row).task().id();
            rowByTask.put(id, row);
            states.put(id, TaskState.PENDING);
        }
        fireTableDataChanged();
    }

    public void resetStates() {
        for (ScheduledTask task : tasks) {
            states.put(task.task().id(), TaskState.PENDING);
        }
        fireTableRowsUpdated(0, Math.max(0, tasks.size() - 1));
    }

    public void applyEvent(SimulationEvent event) {
        Integer row = rowByTask.get(event.taskId());
        if (row == null) {
            return;
        }
        TaskState state = event.kind() == SimulationEvent.Kind.STARTED
                ? TaskState.RUNNING
                : TaskState.COMPLETE;
        states.put(event.taskId(), state);
        fireTableRowsUpdated(row, row);
    }

    public ScheduledTask taskAt(int modelRow) {
        return tasks.get(modelRow);
    }

    public TaskState stateAt(int modelRow) {
        return states.getOrDefault(tasks.get(modelRow).task().id(), TaskState.PENDING);
    }

    @Override
    public int getRowCount() {
        return tasks.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        ScheduledTask scheduled = tasks.get(row);
        return switch (column) {
            case 0 -> scheduled.task().id();
            case 1 -> formatDuration(scheduled.earliestStartMs());
            case 2 -> formatDuration(scheduled.earliestFinishMs());
            case 3 -> formatDuration(scheduled.task().durationMs());
            case 4 -> formatDuration(scheduled.slackMs());
            case 5 -> scheduled.critical() ? "Yes" : "No";
            case 6 -> stateAt(row).label();
            default -> throw new IndexOutOfBoundsException("column " + column);
        };
    }

    private static String formatDuration(long milliseconds) {
        if (milliseconds < 1_000) {
            return milliseconds + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", milliseconds / 1_000.0);
    }

    public enum TaskState {
        PENDING("Pending"),
        RUNNING("Running"),
        COMPLETE("Complete");

        private final String label;

        TaskState(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
