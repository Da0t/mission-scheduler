# Learn the project, then extend it

## Why Java here?

Java is the language; Spring Boot is the application framework. The JVM runs compiled Java bytecode. Spring Boot packages and configures the HTTP server and connects your components.

1. **Types make contracts explicit.** `Controls` and `Snapshot` are records: named, typed data. The compiler catches many mismatches before running the app. Records are only shallowly immutable; snapshots copy their lists to avoid exposing mutable internals.
2. **Managed memory simplifies service code.** The garbage collector reclaims objects so you can focus on behavior. It does not close sockets for you; `@PreDestroy` does that here. GC and OS scheduling can introduce pauses, so this is not a hard real-time system.
3. **Networking is built in.** `DatagramChannel` gives direct access to UDP. Spring Boot handles browser HTTP separately. The web framework does not replace transport-layer concepts.
4. **Concurrency has mature tools.** Java offers locks, executors, futures, nonblocking I/O, and virtual threads. This lab synchronizes state changes so a snapshot cannot observe half an update. Its nonblocking receiver avoids tying up a thread waiting for a datagram. The old mission simulator shows virtual threads and dependency futures.
5. **A reproducible ecosystem supports engineering.** Maven manages versions and builds; JUnit tests the domain behavior; Spring Boot integration tests exercise the actual HTTP application. The wrapper provides a consistent build command across computers.
6. **Portability helps deployment.** The same Java application can run on macOS and Linux with a compatible JVM. Tests still need to check platform assumptions.

Java trades some memory footprint and runtime control for development speed, safety, and tooling. It is a strong fit for network management, backend services, ground systems, and simulation. It is not automatically faster than C++ and does not provide deterministic execution deadlines.

## Why not rewrite it in C or C++?

For your selected networking/backend/mission software direction, keep Java and finish a well-tested system. C++ becomes more relevant when a target job centers on embedded systems, hardware interfaces, native libraries, or strict timing constraints. C and C++ are distinct languages, not interchangeable options.

A useful later extension is a small C++ telemetry agent that speaks a documented binary protocol to this Java dashboard. That creates a reason to learn both languages and demonstrates interoperability. First make the Java version something you can explain without assistance.

## Understand each layer

- **Application:** packet sequence and timestamp, operator controls, metrics.
- **Transport:** UDP sends independent datagrams. It supplies no application-level acknowledgment, retry, ordering, or delivery guarantee. This project does not add retries.
- **Network:** IPv4 loopback connects the two sockets. The relay graph on the screen is modeled; it is not a real IP router.
- **Browser:** HTTP carries API requests and periodic snapshots. It is distinct from the UDP telemetry channel. This version uses polling, not WebSockets.

Read the path: `NetworkApplication` → `NetworkController` → `NetworkLab.tick` → `app.js` render.

Every packet carries two 64-bit big-endian integers: sequence ID and monotonic creation timestamp. Both sockets are in the same JVM, so one monotonic clock can measure elapsed time. You cannot subtract monotonic timestamps from separate machines; a distributed version needs round-trip measurements or clock synchronization.

## Five experiments you should be able to explain

1. Why does switching to Bravo increase latency by roughly 75 ms?
2. Why can some packets still arrive immediately after both relays are disabled? They were already admitted under the previous route.
3. Why is the observed drop rate not exactly the chosen percentage in a short run? Loss decisions are probabilistic.
4. Why does the lifetime average react slowly after adding delay? Earlier packets remain in the average.
5. Why does pausing not erase in-flight traffic? Admission and completion are different stages.

## Next engineering milestones

1. Extract telemetry endpoints into separate processes. Add a protocol version, length validation, malformed-packet tests, and sequence-gap/reordering detection.
2. Add application acknowledgments and bounded retries, then compare reliability and latency against raw UDP. Include deduplication so a retry cannot execute an operation twice.
3. Replace the two-path policy with a weighted graph and shortest-path routing. Test disconnected graphs and recovery; describe it as modeled routing until relays forward real traffic.
4. Connect mission dependencies to live readiness checks. Explain why a scheduled task is blocked when communication is unavailable.
5. Add durable session storage and full replay, structured metrics, and an explicit load-test report. Document packet rate, CPU, memory, and latency distributions on the machine used.
6. For multiuser deployment, add authentication, authorization, session isolation, and tests. The current local single-session design is deliberately smaller.

An interview claim that is true today: “I built a Spring Boot dashboard that transmits real loopback UDP telemetry and demonstrates modeled failover, delay, and packet loss, with socket and HTTP integration tests.” Do not describe the relays as distributed processes or claim production/real-time guarantees that have not been implemented.
