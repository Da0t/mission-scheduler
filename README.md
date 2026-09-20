# Mission Network Lab

A Java 21 + Spring Boot browser application for exploring UDP telemetry, network availability, delay, loss, and failover. This evolves the original mission scheduler into an interactive networking project. All experiment controls live in the browser.

## Start

Install JDK 21 or newer. Double-click **Mission Network Lab.command** on macOS, or run `./mission-scheduler`, then open http://127.0.0.1:8080. The macOS launcher uses the packaged JAR when available and opens the browser once the server responds. Without a packaged JAR, the first launch downloads Maven and dependencies and may take a few minutes. Run `./mvnw package` after source changes to refresh the packaged app. Stop the server with Ctrl+C in its launcher terminal.

No globally installed Maven, Node, or database is required. `./mvnw test` runs tests; `./mvnw package` creates `target/mission-scheduler-2.0.0.jar`. Run it with `java -jar target/mission-scheduler-2.0.0.jar`. `PORT=8081 ./mission-scheduler` selects a different HTTP port (open that port yourself).

## Try this experiment

1. Start the stream. Watch four datagrams per second arrive at the ground station.
2. Disable Relay Alpha. The backup route adds 75 ms of modeled base latency.
3. Add 200 ms delay. Watch the received-packet chart rise; the lifetime average changes more slowly.
4. Add 30% loss. UDP packets disappear without retries; delivery rate declines over time.
5. Disable both relays. New packets drop. Restore one relay to resume delivery.
6. Pause to drain in-flight packets, then export the JSON snapshot. Reset for a fresh comparison.

## What is real, and what is modeled?

**Real:** two loopback UDP sockets, 16-byte binary datagrams, monotonic timing, sequence IDs, HTTP requests to Spring Boot, validation, bounded event/sample buffers, and automated integration tests.

**Modeled:** the diagram's intermediate relays, preferred/backup route selection, base delay, random jitter (0–15 ms), and probabilistic loss. This is a two-path availability model, not a routing-protocol implementation. It does not create separate relay processes or alter the operating system's network routes. Synthetic loss happens before the UDP send. Existing in-flight packets retain the route delay selected at emission. Browser animation indicates an active stream, not individual measured packets.

The backend admits four packets per second. Scheduled packets wait in a priority queue before a real UDP send; the nonblocking receiver measures arrival. A 10 ms scheduling loop adds timing granularity, so this is not a real-time benchmark. The PRNG is seeded for repeatable injection decisions, but wall-clock timing is not deterministic.

Delivery rate = received / (received + dropped); in-flight packets are excluded. Jitter = mean absolute difference between consecutive received packet latencies. Latency includes modeled waiting and local processing. A two-second timeout resolves datagrams not observed at the receiver. The chart contains the last 80 received packets; export contains the current counters, those samples, and the last 100 events, not an unlimited packet capture.

## Architecture

Browser UI → Spring MVC REST controller → synchronized network lab → DatagramChannel sender/receiver.

The frontend is lightweight HTML/CSS/JavaScript served by Spring Boot. It polls `/api/state` every 500 ms. Spring Boot supplies HTTP routing, JSON serialization, dependency injection, scheduling, and application lifecycle. The UDP worker uses nonblocking channels; virtual threads are enabled for supported Spring Boot execution, but they do not make this polling worker parallel.

The original parser, dependency graph, critical-path planner, Swing UI source, and simulator remain available for study. `/api/plan` exposes a small plan through the original engine. That engine is not yet connected to network conditions; the dashboard focuses on the network lab. The default launcher now opens the web application, and `make app` starts the server. Previously generated Swing app bundles are legacy build artifacts.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/state` | Counters, recent arrivals, event log, controls |
| POST | `/api/start` | Start/idempotently continue stream |
| POST | `/api/pause` | Stop admitting packets; drain in-flight work |
| POST | `/api/reset` | Pause and clear the shared session |
| PUT | `/api/controls` | Set primaryUp, backupUp, delayMs (0–400), lossPercent (0–50) |
| GET | `/api/plan` | Example critical-path plan from the original scheduler |

One in-memory session is shared by browser tabs. The server binds to 127.0.0.1 and has no authentication; this version is intended for local use. Session counters disappear on restart.

## Engineering checks

Tests exercise actual socket delivery, pause/drain behavior, loss on complete outage, backup recovery, accounting invariants, reset, bounds validation, HTTP/UI availability, and the five original scheduling regressions. GitHub Actions is configured to run Maven verification on Java 21/Linux; a hosted CI run is only available after pushing the repository.

Read [the learning guide](docs/LEARNING.md) for Java benefits, networking concepts, exercises, and the next steps toward a stronger portfolio project.

Independent educational project; no affiliation with Lockheed Martin or any operational mission system.
