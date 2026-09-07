package dev.datnguyen.missionscheduler;

import dev.datnguyen.missionscheduler.app.MissionTableModel;
import dev.datnguyen.missionscheduler.io.MissionParser;
import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.model.MissionTask;
import dev.datnguyen.missionscheduler.planning.MissionPlan;
import dev.datnguyen.missionscheduler.planning.MissionPlanner;
import dev.datnguyen.missionscheduler.simulation.MissionSimulator;
import dev.datnguyen.missionscheduler.simulation.SimulationEvent;
import dev.datnguyen.missionscheduler.simulation.SimulationReport;

import java.util.ArrayList;
import java.util.List;

public final class MissionSchedulerTests {
    private static int passed;

    private MissionSchedulerTests() {
    }

    public static void main(String[] args) throws Exception {
        run("parse and critical path", MissionSchedulerTests::testParseAndCriticalPath);
        run("missing dependency", MissionSchedulerTests::testMissingDependency);
        run("cycle detection", MissionSchedulerTests::testCycleDetection);
        run("dependency-aware simulation", MissionSchedulerTests::testSimulationOrdering);
        run("desktop table state", MissionSchedulerTests::testDesktopTableState);
        System.out.printf("%n%d tests passed%n", passed);
    }

    private static void testParseAndCriticalPath() {
        Mission mission = new MissionParser().parseLines(List.of(
                "weather|1200||Load weather data",
                "power|900||Power avionics",
                "verify|1100|power|Verify communication links",
                "window|700|weather|Compute launch window",
                "poll|600|verify,window|Run go/no-go poll",
                "arm|500|poll|Arm automated sequence",
                "launch|200|arm|Commit launch command"), "test.mission");

        MissionPlan plan = new MissionPlanner().plan(mission);
        check(plan.totalDurationMs() == 3_300,
                "duration was " + plan.totalDurationMs() + "ms, expected 3300ms");
        List<String> critical = plan.criticalTasks().stream()
                .map(task -> task.task().id())
                .toList();
        check(critical.equals(List.of("power", "verify", "poll", "arm", "launch")),
                "critical tasks were " + critical);
    }

    private static void testMissingDependency() {
        expectIllegalArgument(() -> new MissionParser().parseLines(
                List.of("launch|100|missing|Launch"), "missing.mission"));
    }

    private static void testCycleDetection() {
        Mission mission = new Mission(List.of(
                new MissionTask("alpha", 10, List.of("bravo"), "Alpha"),
                new MissionTask("bravo", 10, List.of("alpha"), "Bravo")));
        expectIllegalArgument(() -> new MissionPlanner().plan(mission));
    }

    private static void testSimulationOrdering() throws Exception {
        Mission mission = new Mission(List.of(
                new MissionTask("alpha", 20, List.of(), "Alpha"),
                new MissionTask("bravo", 20, List.of("alpha"), "Bravo"),
                new MissionTask("charlie", 20, List.of(), "Charlie")));

        List<SimulationEvent> observed = new ArrayList<>();
        SimulationReport report = new MissionSimulator().simulate(mission, 100, observed::add);
        check(report.plannedDuration().toMillis() == 40, "planned duration should be 40ms");
        check(observed.size() == 6, "expected 6 events, got " + observed.size());

        long alphaCompleted = sequenceOf(observed, "alpha", SimulationEvent.Kind.COMPLETED);
        long bravoStarted = sequenceOf(observed, "bravo", SimulationEvent.Kind.STARTED);
        check(alphaCompleted < bravoStarted, "bravo started before alpha completed");
    }

    private static void testDesktopTableState() {
        Mission mission = new Mission(List.of(
                new MissionTask("alpha", 20, List.of(), "Alpha")));
        MissionPlan plan = new MissionPlanner().plan(mission);
        MissionTableModel model = new MissionTableModel();
        model.setPlan(plan);

        check(model.getRowCount() == 1, "table should contain one task");
        check(model.stateAt(0) == MissionTableModel.TaskState.PENDING,
                "task should initially be pending");

        model.applyEvent(new SimulationEvent(
                1, SimulationEvent.Kind.STARTED, "alpha", "Alpha", 1));
        check(model.stateAt(0) == MissionTableModel.TaskState.RUNNING,
                "started task should be running");

        model.applyEvent(new SimulationEvent(
                2, SimulationEvent.Kind.COMPLETED, "alpha", "Alpha", 2));
        check(model.stateAt(0) == MissionTableModel.TaskState.COMPLETE,
                "completed task should be complete");
    }

    private static long sequenceOf(
            List<SimulationEvent> events,
            String taskId,
            SimulationEvent.Kind kind) {
        return events.stream()
                .filter(event -> event.taskId().equals(taskId) && event.kind() == kind)
                .findFirst()
                .orElseThrow()
                .sequence();
    }

    private static void run(String name, CheckedRunnable test) throws Exception {
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected path.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
