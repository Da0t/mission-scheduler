package dev.datnguyen.missionscheduler.network.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class ScheduleStore {
    public record Version(long revision, String createdAt, Scheduling.Scenario scenario, Scheduling.Result result, List<String> changes) {}
    public record State(List<Version> versions) {}
    private final ObjectMapper mapper;
    private final Path file;
    private State state;
    public ScheduleStore(ObjectMapper mapper, @Value("${mission.store:./data/schedules.json}") String file) throws IOException {
        this.mapper = mapper; this.file = Path.of(file).toAbsolutePath();
        if (Files.exists(this.file)) {
            state = mapper.readValue(this.file.toFile(), State.class);
            if (state.versions == null || state.versions.isEmpty()) throw new IOException("Schedule store is empty; restore a valid backup.");
        } else state = new State(List.of(new Version(1, Instant.now().toString(), Scheduling.demo(), Scheduling.solve(Scheduling.demo()), List.of("Created sample scenario"))));
    }
    public synchronized State state() { return state; }
    public synchronized State save(long expectedRevision, Scheduling.Scenario scenario) throws IOException {
        Version previous = state.versions.get(0);
        if (expectedRevision != previous.revision) throw new StaleRevision();
        Scheduling.Result result = Scheduling.solve(scenario);
        Set<String> before = new HashSet<>(), after = new HashSet<>();
        previous.result.decisions().stream().filter(Scheduling.Decision::selected).forEach(d -> before.add(d.contact().id()));
        result.decisions().stream().filter(Scheduling.Decision::selected).forEach(d -> after.add(d.contact().id()));
        List<String> changes = new ArrayList<>();
        before.stream().filter(id -> !after.contains(id)).sorted().forEach(id -> changes.add("Removed " + id));
        after.stream().filter(id -> !before.contains(id)).sorted().forEach(id -> changes.add("Added " + id));
        if (changes.isEmpty()) changes.add("Selected contact IDs unchanged; inspect scenario for input changes");
        List<Version> versions = new ArrayList<>(); versions.add(new Version(previous.revision+1, Instant.now().toString(), scenario, result, List.copyOf(changes)));
        versions.addAll(state.versions.subList(0, Math.min(19, state.versions.size())));
        State next = new State(List.copyOf(versions));
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "schedule-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), next);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        state = next;
        return state;
    }
    public static class StaleRevision extends RuntimeException {}
}
