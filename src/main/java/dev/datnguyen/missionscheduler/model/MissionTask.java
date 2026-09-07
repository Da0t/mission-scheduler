package dev.datnguyen.missionscheduler.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MissionTask(
        String id,
        long durationMs,
        List<String> dependencies,
        String description) {

    public MissionTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(description, "description");
        if (id.isBlank()) {
            throw new IllegalArgumentException("task id cannot be blank");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("task duration must be positive: " + id);
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("task description cannot be blank: " + id);
        }

        dependencies = List.copyOf(dependencies);
        Set<String> uniqueDependencies = new HashSet<>();
        for (String dependency : dependencies) {
            if (dependency == null || dependency.isBlank()) {
                throw new IllegalArgumentException("task has an empty dependency: " + id);
            }
            if (dependency.equals(id)) {
                throw new IllegalArgumentException("task cannot depend on itself: " + id);
            }
            if (!uniqueDependencies.add(dependency)) {
                throw new IllegalArgumentException(
                        "task has duplicate dependency " + dependency + ": " + id);
            }
        }
    }
}
