package dev.datnguyen.missionscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Mission {
    private final Map<String, MissionTask> tasksById;

    public Mission(List<MissionTask> tasks) {
        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("mission must contain at least one task");
        }

        Map<String, MissionTask> indexed = new LinkedHashMap<>();
        for (MissionTask task : tasks) {
            Objects.requireNonNull(task, "task");
            if (indexed.putIfAbsent(task.id(), task) != null) {
                throw new IllegalArgumentException("duplicate task id: " + task.id());
            }
        }
        tasksById = Collections.unmodifiableMap(indexed);
    }

    public List<MissionTask> tasks() {
        return List.copyOf(new ArrayList<>(tasksById.values()));
    }

    public MissionTask task(String id) {
        MissionTask task = tasksById.get(id);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + id);
        }
        return task;
    }

    public boolean contains(String id) {
        return tasksById.containsKey(id);
    }

    public int size() {
        return tasksById.size();
    }
}
