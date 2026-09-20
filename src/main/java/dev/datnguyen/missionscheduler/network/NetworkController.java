package dev.datnguyen.missionscheduler.network;

import dev.datnguyen.missionscheduler.io.MissionParser;
import dev.datnguyen.missionscheduler.planning.MissionPlanner;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NetworkController {
    private final NetworkLab lab;
    public NetworkController(NetworkLab lab) { this.lab = lab; }
    @GetMapping("/state") public NetworkLab.Snapshot state() { return lab.snapshot(); }
    @PostMapping("/start") public NetworkLab.Snapshot start() { return lab.start(); }
    @PostMapping("/pause") public NetworkLab.Snapshot pause() { return lab.stop(); }
    @PostMapping("/reset") public NetworkLab.Snapshot reset() throws IOException { return lab.reset(); }
    @PutMapping("/controls") public NetworkLab.Snapshot controls(@RequestBody NetworkLab.Controls controls) { return lab.configure(controls); }
    @GetMapping("/plan") public Map<String, Object> plan() {
        var mission = new MissionParser().parseLines(List.of(
            "power|900||Power ground station",
            "weather|1200||Check environment",
            "link|1100|power|Verify telemetry link",
            "ready|600|weather,link|Confirm readiness"), "network-demo");
        var plan = new MissionPlanner().plan(mission);
        return Map.of("durationMs", plan.totalDurationMs(), "tasks", plan.tasks());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException e) { return Map.of("error", e.getMessage()); }
}
