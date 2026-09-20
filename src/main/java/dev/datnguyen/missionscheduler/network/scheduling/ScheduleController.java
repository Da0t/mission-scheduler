package dev.datnguyen.missionscheduler.network.scheduling;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    public record Update(long expectedRevision, Scheduling.Scenario scenario) {}
    private final ScheduleStore store;
    public ScheduleController(ScheduleStore store) { this.store = store; }
    @GetMapping public ScheduleStore.State state() { return store.state(); }
    @GetMapping("/example") public Scheduling.Scenario example() { return Scheduling.demo(); }
    @PostMapping public ScheduleStore.State save(@RequestBody Update update) throws IOException {
        if (update.scenario == null) throw new IllegalArgumentException("A scenario is required.");
        return store.save(update.expectedRevision, update.scenario);
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, java.time.DateTimeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(Exception e) { return Map.of("error", "Invalid scenario: check date, unique IDs, station references, priorities 1–100, windows 0–1440, and the 24-contact limit."); }
    @ExceptionHandler(ScheduleStore.StaleRevision.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> stale() { return Map.of("error", "Another tab saved a newer version. Reload before saving."); }
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String,String> persistence(IOException e) { return Map.of("error", "Could not save the schedule. Existing saved state was preserved."); }
}
