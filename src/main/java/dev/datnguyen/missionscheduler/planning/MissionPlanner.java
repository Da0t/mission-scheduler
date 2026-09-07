package dev.datnguyen.missionscheduler.planning;

import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.model.MissionTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class MissionPlanner {
    public MissionPlan plan(Mission mission) {
        List<MissionTask> sourceOrder = mission.tasks();
        Map<String, Integer> sourceIndex = new HashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();

        for (int index = 0; index < sourceOrder.size(); index++) {
            MissionTask task = sourceOrder.get(index);
            sourceIndex.put(task.id(), index);
            indegree.put(task.id(), task.dependencies().size());
            dependents.put(task.id(), new ArrayList<>());
        }
        for (MissionTask task : sourceOrder) {
            for (String dependency : task.dependencies()) {
                if (!mission.contains(dependency)) {
                    throw new IllegalArgumentException(
                            "task " + task.id() + " depends on missing task " + dependency);
                }
                dependents.get(dependency).add(task.id());
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.comparingInt(sourceIndex::get));
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<String> topologicalOrder = new ArrayList<>(sourceOrder.size());
        Map<String, Long> earliestStart = new HashMap<>();
        Map<String, Long> earliestFinish = new HashMap<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            MissionTask task = mission.task(id);
            long start = 0;
            for (String dependency : task.dependencies()) {
                start = Math.max(start, earliestFinish.get(dependency));
            }
            long finish;
            try {
                finish = Math.addExact(start, task.durationMs());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("mission duration overflow at task " + id, exception);
            }

            earliestStart.put(id, start);
            earliestFinish.put(id, finish);
            topologicalOrder.add(id);

            for (String dependent : dependents.get(id)) {
                int remaining = indegree.compute(dependent, (ignored, degree) -> degree - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (topologicalOrder.size() != sourceOrder.size()) {
            List<String> cycleTasks = indegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            throw new IllegalArgumentException(
                    "mission contains a dependency cycle involving: " + String.join(", ", cycleTasks));
        }

        long totalDuration = earliestFinish.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        Map<String, Long> latestStart = new HashMap<>();
        List<String> reverseOrder = new ArrayList<>(topologicalOrder);
        Collections.reverse(reverseOrder);
        for (String id : reverseOrder) {
            MissionTask task = mission.task(id);
            List<String> nextTasks = dependents.get(id);
            long latestFinish = nextTasks.isEmpty()
                    ? totalDuration
                    : nextTasks.stream().mapToLong(latestStart::get).min().orElseThrow();
            latestStart.put(id, latestFinish - task.durationMs());
        }

        List<ScheduledTask> scheduledTasks = new ArrayList<>(topologicalOrder.size());
        for (String id : topologicalOrder) {
            MissionTask task = mission.task(id);
            long start = earliestStart.get(id);
            long latest = latestStart.get(id);
            scheduledTasks.add(new ScheduledTask(
                    task,
                    start,
                    earliestFinish.get(id),
                    latest,
                    latest - start));
        }
        return new MissionPlan(scheduledTasks, totalDuration);
    }
}
