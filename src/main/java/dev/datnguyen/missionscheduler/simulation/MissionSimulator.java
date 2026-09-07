package dev.datnguyen.missionscheduler.simulation;

import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.model.MissionTask;
import dev.datnguyen.missionscheduler.planning.MissionPlan;
import dev.datnguyen.missionscheduler.planning.MissionPlanner;
import dev.datnguyen.missionscheduler.planning.ScheduledTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class MissionSimulator {
    public SimulationReport simulate(
            Mission mission,
            double speed,
            Consumer<SimulationEvent> observer) throws InterruptedException {
        Objects.requireNonNull(mission, "mission");
        Objects.requireNonNull(observer, "observer");
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalArgumentException("speed must be a positive finite number");
        }

        MissionPlan plan = new MissionPlanner().plan(mission);
        long simulationStarted = System.nanoTime();
        Object eventLock = new Object();
        AtomicLong eventSequence = new AtomicLong();
        List<SimulationEvent> events = new ArrayList<>();
        Map<String, CompletableFuture<Void>> completions = new HashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (ScheduledTask scheduledTask : plan.tasks()) {
                MissionTask task = scheduledTask.task();
                CompletableFuture<?>[] dependencies = task.dependencies().stream()
                        .map(completions::get)
                        .toArray(CompletableFuture<?>[]::new);
                CompletableFuture<Void> dependenciesComplete = CompletableFuture.allOf(dependencies);
                CompletableFuture<Void> completion = dependenciesComplete.thenRunAsync(() -> {
                    emit(eventLock, events, eventSequence, observer,
                            SimulationEvent.Kind.STARTED, task, simulationStarted);
                    try {
                        Thread.sleep(scaledDurationMs(task.durationMs(), speed));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new CompletionException(exception);
                    }
                    emit(eventLock, events, eventSequence, observer,
                            SimulationEvent.Kind.COMPLETED, task, simulationStarted);
                }, executor);
                completions.put(task.id(), completion);
            }

            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                    completions.values().toArray(CompletableFuture<?>[]::new));
            try {
                allTasks.get();
            } catch (ExecutionException exception) {
                Throwable cause = unwrap(exception);
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) cause;
                }
                throw new IllegalStateException("simulation task failed: " + cause.getMessage(), cause);
            }
        } finally {
            executor.shutdownNow();
        }

        Duration actualDuration = Duration.ofNanos(System.nanoTime() - simulationStarted);
        return new SimulationReport(
                actualDuration,
                Duration.ofMillis(plan.totalDurationMs()),
                events);
    }

    private static void emit(
            Object lock,
            List<SimulationEvent> events,
            AtomicLong sequence,
            Consumer<SimulationEvent> observer,
            SimulationEvent.Kind kind,
            MissionTask task,
            long simulationStarted) {
        synchronized (lock) {
            SimulationEvent event = new SimulationEvent(
                    sequence.incrementAndGet(),
                    kind,
                    task.id(),
                    task.description(),
                    System.nanoTime() - simulationStarted);
            events.add(event);
            observer.accept(event);
        }
    }

    private static long scaledDurationMs(long durationMs, double speed) {
        return Math.max(1, (long) Math.ceil(durationMs / speed));
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
