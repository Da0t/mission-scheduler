package dev.datnguyen.missionscheduler.io;

import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.model.MissionTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class MissionParser {
    private static final Pattern TASK_ID = Pattern.compile("[a-z][a-z0-9_-]*");

    public Mission parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return parseLines(lines, path.toString());
    }

    public Mission parseLines(List<String> lines, String sourceName) {
        if (lines == null) {
            throw new IllegalArgumentException("mission lines cannot be null");
        }
        String source = sourceName == null || sourceName.isBlank() ? "<mission>" : sourceName;
        List<MissionTask> tasks = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\|", -1);
            if (fields.length != 4) {
                throw formatError(source, lineNumber,
                        "expected id|duration_ms|dependencies|description");
            }

            String id = fields[0].trim();
            if (!TASK_ID.matcher(id).matches()) {
                throw formatError(source, lineNumber,
                        "invalid task id " + quote(id) + "; use lowercase letters, numbers, _ or -");
            }
            if (!seenIds.add(id)) {
                throw formatError(source, lineNumber, "duplicate task id " + quote(id));
            }

            long durationMs;
            try {
                durationMs = Long.parseLong(fields[1].trim());
            } catch (NumberFormatException exception) {
                throw formatError(source, lineNumber, "duration must be an integer number of milliseconds");
            }
            if (durationMs <= 0) {
                throw formatError(source, lineNumber, "duration must be positive");
            }

            List<String> dependencies = parseDependencies(fields[2], source, lineNumber);
            String description = fields[3].trim();
            if (description.isEmpty()) {
                throw formatError(source, lineNumber, "description cannot be empty");
            }

            try {
                tasks.add(new MissionTask(id, durationMs, dependencies, description));
            } catch (IllegalArgumentException exception) {
                throw formatError(source, lineNumber, exception.getMessage());
            }
        }

        Mission mission;
        try {
            mission = new Mission(tasks);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(source + ": " + exception.getMessage(), exception);
        }

        for (MissionTask task : mission.tasks()) {
            for (String dependency : task.dependencies()) {
                if (!mission.contains(dependency)) {
                    throw new IllegalArgumentException(
                            source + ": task " + quote(task.id())
                                    + " depends on missing task " + quote(dependency));
                }
            }
        }
        return mission;
    }

    private static List<String> parseDependencies(String field, String source, int lineNumber) {
        if (field.isBlank()) {
            return List.of();
        }

        Set<String> dependencies = new LinkedHashSet<>();
        for (String rawDependency : field.split(",", -1)) {
            String dependency = rawDependency.trim();
            if (!TASK_ID.matcher(dependency).matches()) {
                throw formatError(source, lineNumber,
                        "invalid dependency id " + quote(dependency));
            }
            if (!dependencies.add(dependency)) {
                throw formatError(source, lineNumber,
                        "duplicate dependency " + quote(dependency));
            }
        }
        return List.copyOf(dependencies);
    }

    private static IllegalArgumentException formatError(String source, int line, String message) {
        return new IllegalArgumentException(source + ":" + line + ": " + message);
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
