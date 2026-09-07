package dev.datnguyen.missionscheduler.planning;

import java.util.List;

public record MissionPlan(List<ScheduledTask> tasks, long totalDurationMs) {
    public MissionPlan {
        tasks = List.copyOf(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("plan cannot be empty");
        }
        if (totalDurationMs <= 0) {
            throw new IllegalArgumentException("total duration must be positive");
        }
    }

    public List<ScheduledTask> criticalTasks() {
        return tasks.stream().filter(ScheduledTask::critical).toList();
    }
}
