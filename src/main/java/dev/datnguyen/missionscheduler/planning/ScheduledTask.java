package dev.datnguyen.missionscheduler.planning;

import dev.datnguyen.missionscheduler.model.MissionTask;

import java.util.Objects;

public record ScheduledTask(
        MissionTask task,
        long earliestStartMs,
        long earliestFinishMs,
        long latestStartMs,
        long slackMs) {

    public ScheduledTask {
        Objects.requireNonNull(task, "task");
        if (earliestStartMs < 0 || earliestFinishMs < earliestStartMs) {
            throw new IllegalArgumentException("invalid earliest timing for " + task.id());
        }
        if (latestStartMs < earliestStartMs || slackMs != latestStartMs - earliestStartMs) {
            throw new IllegalArgumentException("invalid slack for " + task.id());
        }
    }

    public boolean critical() {
        return slackMs == 0;
    }
}
