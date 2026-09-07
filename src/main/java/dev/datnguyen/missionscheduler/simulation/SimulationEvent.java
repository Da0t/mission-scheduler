package dev.datnguyen.missionscheduler.simulation;

import java.util.Objects;

public record SimulationEvent(
        long sequence,
        Kind kind,
        String taskId,
        String description,
        long elapsedNanos) {

    public SimulationEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(description, "description");
        if (sequence < 1 || elapsedNanos < 0) {
            throw new IllegalArgumentException("invalid simulation event timing");
        }
    }

    public enum Kind {
        STARTED,
        COMPLETED
    }
}
