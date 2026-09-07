package dev.datnguyen.missionscheduler.simulation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record SimulationReport(
        Duration actualDuration,
        Duration plannedDuration,
        List<SimulationEvent> events) {

    public SimulationReport {
        Objects.requireNonNull(actualDuration, "actualDuration");
        Objects.requireNonNull(plannedDuration, "plannedDuration");
        events = List.copyOf(events);
    }
}
