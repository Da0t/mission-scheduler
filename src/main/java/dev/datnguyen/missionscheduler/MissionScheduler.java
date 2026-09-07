package dev.datnguyen.missionscheduler;

import dev.datnguyen.missionscheduler.app.MissionSchedulerApp;
import dev.datnguyen.missionscheduler.io.MissionParser;
import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.planning.MissionPlan;
import dev.datnguyen.missionscheduler.planning.MissionPlanner;
import dev.datnguyen.missionscheduler.planning.ScheduledTask;
import dev.datnguyen.missionscheduler.simulation.MissionSimulator;
import dev.datnguyen.missionscheduler.simulation.SimulationEvent;
import dev.datnguyen.missionscheduler.simulation.SimulationReport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MissionScheduler {
    private static final String VERSION = "1.0.0";

    private final PrintStream output;
    private final PrintStream errorOutput;

    private MissionScheduler(PrintStream output, PrintStream errorOutput) {
        this.output = output;
        this.errorOutput = errorOutput;
    }

    public static void main(String[] args) {
        int exitCode = new MissionScheduler(System.out, System.err).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args.length == 0) {
            args = new String[]{"app"};
        }

        String commandName = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        try {
            return switch (commandName) {
                case "app" -> runApp(commandArgs);
                case "validate" -> runValidate(commandArgs);
                case "plan" -> runPlan(commandArgs);
                case "simulate" -> runSimulate(commandArgs);
                case "help", "-h", "--help" -> {
                    printUsage(output);
                    yield 0;
                }
                case "version", "--version" -> {
                    output.println("mission-scheduler " + VERSION);
                    yield 0;
                }
                default -> {
                    errorOutput.printf("unknown command %s%n%n", quote(commandName));
                    printUsage(errorOutput);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException exception) {
            errorOutput.printf("%s: %s%n", commandName, exception.getMessage());
            return 2;
        } catch (IOException exception) {
            errorOutput.printf("%s: %s%n", commandName, exception.getMessage());
            return 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            errorOutput.println("simulate: interrupted");
            return 130;
        } catch (IllegalStateException exception) {
            errorOutput.printf("%s: %s%n", commandName, exception.getMessage());
            return 1;
        }
    }

    private int runApp(String[] args) {
        ParsedCommand command = ParsedCommand.parse(args, Set.of(), Set.of("help"));
        if (command.hasFlag("help")) {
            printAppUsage(output);
            return 0;
        }
        MissionSchedulerApp.launch(command.optionalPath());
        return 0;
    }

    private int runValidate(String[] args) throws IOException {
        ParsedCommand command = ParsedCommand.parse(args, Set.of(), Set.of("help"));
        if (command.hasFlag("help")) {
            printValidateUsage(output);
            return 0;
        }

        Path path = command.singlePath();
        Mission mission = new MissionParser().parse(path);
        MissionPlan plan = new MissionPlanner().plan(mission);
        output.printf(
                "valid mission: %d tasks, planned duration %s, %d critical tasks%n",
                mission.size(),
                formatDuration(plan.totalDurationMs()),
                plan.criticalTasks().size());
        return 0;
    }

    private int runPlan(String[] args) throws IOException {
        ParsedCommand command = ParsedCommand.parse(args, Set.of(), Set.of("json", "help"));
        if (command.hasFlag("help")) {
            printPlanUsage(output);
            return 0;
        }

        Path path = command.singlePath();
        MissionPlan plan = new MissionPlanner().plan(new MissionParser().parse(path));
        if (command.hasFlag("json")) {
            printPlanJson(path, plan);
        } else {
            printPlanTable(path, plan);
        }
        return 0;
    }

    private int runSimulate(String[] args) throws IOException, InterruptedException {
        ParsedCommand command = ParsedCommand.parse(args, Set.of("speed"), Set.of("json", "help"));
        if (command.hasFlag("help")) {
            printSimulateUsage(output);
            return 0;
        }

        double speed = parsePositiveDouble(command.value("speed", "100"), "speed");
        Path path = command.singlePath();
        Mission mission = new MissionParser().parse(path);
        boolean json = command.hasFlag("json");

        SimulationReport report = new MissionSimulator().simulate(
                mission,
                speed,
                event -> printEvent(event, json));
        if (json) {
            output.printf(
                    Locale.ROOT,
                    "{\"summary\":true,\"tasks\":%d,\"speed\":%.2f,"
                            + "\"planned_duration_ms\":%d,\"actual_duration_ms\":%.3f}%n",
                    mission.size(),
                    speed,
                    report.plannedDuration().toMillis(),
                    nanosToMilliseconds(report.actualDuration().toNanos()));
        } else {
            output.printf(
                    Locale.ROOT,
                    "%nSimulation complete: %d tasks at %.2fx in %s (planned parallel duration %s)%n",
                    mission.size(),
                    speed,
                    formatDuration(report.actualDuration()),
                    formatDuration(report.plannedDuration()));
        }
        return 0;
    }

    private void printPlanTable(Path path, MissionPlan plan) {
        output.println("Mission: " + path);
        output.printf(
                "%-20s %10s %10s %10s %10s %-9s %s%n",
                "TASK", "START", "FINISH", "DURATION", "SLACK", "CRITICAL", "DEPENDENCIES");
        for (ScheduledTask scheduled : plan.tasks()) {
            output.printf(
                    "%-20s %10s %10s %10s %10s %-9s %s%n",
                    scheduled.task().id(),
                    formatDuration(scheduled.earliestStartMs()),
                    formatDuration(scheduled.earliestFinishMs()),
                    formatDuration(scheduled.task().durationMs()),
                    formatDuration(scheduled.slackMs()),
                    scheduled.critical() ? "yes" : "no",
                    scheduled.task().dependencies().isEmpty()
                            ? "-"
                            : String.join(",", scheduled.task().dependencies()));
            output.printf("  %s%n", scheduled.task().description());
        }

        output.printf("%nEarliest completion: %s%n", formatDuration(plan.totalDurationMs()));
        output.printf(
                "Critical tasks: %s%n",
                String.join(" -> ", plan.criticalTasks().stream()
                        .map(task -> task.task().id())
                        .toList()));
    }

    private void printPlanJson(Path path, MissionPlan plan) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"mission\": ").append(quote(path.toString())).append(",\n")
                .append("  \"total_duration_ms\": ").append(plan.totalDurationMs()).append(",\n")
                .append("  \"tasks\": [\n");
        for (int index = 0; index < plan.tasks().size(); index++) {
            ScheduledTask scheduled = plan.tasks().get(index);
            json.append("    {\"id\": ").append(quote(scheduled.task().id()))
                    .append(", \"description\": ").append(quote(scheduled.task().description()))
                    .append(", \"duration_ms\": ").append(scheduled.task().durationMs())
                    .append(", \"earliest_start_ms\": ").append(scheduled.earliestStartMs())
                    .append(", \"earliest_finish_ms\": ").append(scheduled.earliestFinishMs())
                    .append(", \"latest_start_ms\": ").append(scheduled.latestStartMs())
                    .append(", \"slack_ms\": ").append(scheduled.slackMs())
                    .append(", \"critical\": ").append(scheduled.critical())
                    .append(", \"dependencies\": [");
            for (int dependencyIndex = 0;
                    dependencyIndex < scheduled.task().dependencies().size();
                    dependencyIndex++) {
                if (dependencyIndex > 0) {
                    json.append(", ");
                }
                json.append(quote(scheduled.task().dependencies().get(dependencyIndex)));
            }
            json.append("]}");
            if (index + 1 < plan.tasks().size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}");
        output.println(json);
    }

    private void printEvent(SimulationEvent event, boolean json) {
        if (json) {
            output.printf(
                    Locale.ROOT,
                    "{\"sequence\":%d,\"event\":%s,\"task\":%s,"
                            + "\"description\":%s,\"elapsed_ms\":%.3f}%n",
                    event.sequence(),
                    quote(event.kind().name().toLowerCase(Locale.ROOT)),
                    quote(event.taskId()),
                    quote(event.description()),
                    nanosToMilliseconds(event.elapsedNanos()));
            return;
        }

        output.printf(
                Locale.ROOT,
                "[T+%8.3fms] %-9s %-20s %s%n",
                nanosToMilliseconds(event.elapsedNanos()),
                event.kind().name(),
                event.taskId(),
                event.description());
    }

    private static double parsePositiveDouble(String value, String name) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0) {
                throw new IllegalArgumentException(name + " must be a positive finite number");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a number: " + value, exception);
        }
    }

    private static String formatDuration(long milliseconds) {
        if (milliseconds < 1_000) {
            return milliseconds + "ms";
        }
        return String.format(Locale.ROOT, "%.2fs", milliseconds / 1_000.0);
    }

    private static String formatDuration(Duration duration) {
        double milliseconds = nanosToMilliseconds(duration.toNanos());
        if (milliseconds < 1_000) {
            return String.format(Locale.ROOT, "%.3fms", milliseconds);
        }
        return String.format(Locale.ROOT, "%.2fs", milliseconds / 1_000.0);
    }

    private static double nanosToMilliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static void printUsage(PrintStream stream) {
        stream.println("""
                mission-scheduler - validate, plan, and simulate dependency-aware operations

                Usage:
                  mission-scheduler
                  mission-scheduler app [MISSION_FILE]
                  mission-scheduler COMMAND MISSION_FILE [options]

                Commands:
                  app       Launch the desktop application (default)
                  validate  Check syntax, references, and dependency cycles
                  plan      Compute parallel timing, slack, and critical tasks
                  simulate  Execute the dependency graph using virtual threads
                  version   Print the version

                Run "mission-scheduler COMMAND --help" for command-specific help.""");
    }

    private static void printAppUsage(PrintStream stream) {
        stream.println("""
                Usage: mission-scheduler app [MISSION_FILE]

                Launch the desktop application. With no mission file, the bundled demo opens.""");
    }

    private static void printValidateUsage(PrintStream stream) {
        stream.println("Usage: mission-scheduler validate MISSION_FILE");
    }

    private static void printPlanUsage(PrintStream stream) {
        stream.println("""
                Usage: mission-scheduler plan MISSION_FILE [--json]

                  --json  Emit a machine-readable plan""");
    }

    private static void printSimulateUsage(PrintStream stream) {
        stream.println("""
                Usage: mission-scheduler simulate MISSION_FILE [options]

                  --speed NUMBER  Time-compression factor (default: 100)
                  --json          Emit newline-delimited JSON events""");
    }

    private record ParsedCommand(Map<String, String> values, Set<String> flags, List<String> positionals) {
        static ParsedCommand parse(String[] args, Set<String> valueOptions, Set<String> flagOptions) {
            Map<String, String> values = new HashMap<>();
            Set<String> flags = new HashSet<>();
            List<String> positionals = new ArrayList<>();

            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (argument.equals("-h")) {
                    argument = "--help";
                }
                if (!argument.startsWith("--")) {
                    positionals.add(argument);
                    continue;
                }

                String option = argument.substring(2);
                String inlineValue = null;
                int equals = option.indexOf('=');
                if (equals >= 0) {
                    inlineValue = option.substring(equals + 1);
                    option = option.substring(0, equals);
                }

                if (flagOptions.contains(option)) {
                    if (inlineValue != null) {
                        throw new IllegalArgumentException("flag --" + option + " does not accept a value");
                    }
                    if (!flags.add(option)) {
                        throw new IllegalArgumentException("duplicate flag --" + option);
                    }
                    continue;
                }
                if (!valueOptions.contains(option)) {
                    throw new IllegalArgumentException("unknown option --" + option);
                }
                if (values.containsKey(option)) {
                    throw new IllegalArgumentException("duplicate option --" + option);
                }
                if (inlineValue == null) {
                    if (++index >= args.length) {
                        throw new IllegalArgumentException("missing value for --" + option);
                    }
                    inlineValue = args[index];
                }
                if (inlineValue.isEmpty()) {
                    throw new IllegalArgumentException("empty value for --" + option);
                }
                values.put(option, inlineValue);
            }
            return new ParsedCommand(Map.copyOf(values), Set.copyOf(flags), List.copyOf(positionals));
        }

        boolean hasFlag(String name) {
            return flags.contains(name);
        }

        String value(String name, String defaultValue) {
            return values.getOrDefault(name, defaultValue);
        }

        Path singlePath() {
            if (positionals.size() != 1) {
                throw new IllegalArgumentException("exactly one mission file is required");
            }
            return Path.of(positionals.getFirst());
        }

        Path optionalPath() {
            if (positionals.size() > 1) {
                throw new IllegalArgumentException("at most one mission file may be opened");
            }
            return positionals.isEmpty() ? null : Path.of(positionals.getFirst());
        }
    }
}
