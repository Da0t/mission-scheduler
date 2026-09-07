# mission-scheduler

`mission-scheduler` is an independent Java 21 CLI for validating, planning, and simulating dependency-aware operations. It is deliberately a separate problem and codebase from the Go networking project.

The example domain is a fictional launch sequence, but the underlying ideas apply to build systems, workflow engines, job schedulers, and project planning. This educational project is not affiliated with or based on internal systems from SpaceX or any other launch provider.

## What it demonstrates

- Directed acyclic graphs and deterministic topological sorting.
- Missing-reference and dependency-cycle detection.
- Earliest start/finish calculations.
- Latest start and slack calculations.
- Critical-path analysis.
- Dependency-aware parallel execution with Java virtual threads.
- Immutable records, clear domain boundaries, and zero third-party dependencies.
- Human-readable and JSON CLI output.

## Requirements

- JDK 21 or newer.
- `make` for the convenience targets and launcher.

Maven and Gradle are not required.

## Build and test

```bash
cd /Users/datnguyen/Desktop/Projects/mission-scheduler
make test
make build
```

The executable JAR is generated at `build/mission-scheduler.jar`. The launcher recompiles changed sources automatically.

## Try it

Validate the included mission:

```bash
./mission-scheduler validate examples/demo.mission
```

Display its schedule and critical tasks:

```bash
./mission-scheduler plan examples/demo.mission
```

Run a time-compressed simulation using virtual threads:

```bash
./mission-scheduler simulate examples/demo.mission --speed 100
```

Request machine-readable output:

```bash
./mission-scheduler plan examples/demo.mission --json
./mission-scheduler simulate examples/demo.mission --speed 100 --json
```

## Mission file format

Mission files use four pipe-separated fields:

```text
id|duration_ms|comma_separated_dependencies|description
```

Blank lines and lines beginning with `#` are ignored. Dependencies may refer to tasks declared later in the file.

```text
power|900||Power avionics
verify|1100|power|Verify communication links
poll|600|verify,weather|Run the go/no-go poll
```

The planner rejects duplicate task IDs, missing dependencies, self-dependencies, malformed durations, and cycles.

## Core algorithm

1. Build an adjacency list and dependency count for each task.
2. Use Kahn's algorithm to produce a deterministic topological order.
3. Walk forward to calculate earliest start and finish times.
4. Walk backward to calculate latest start times.
5. Compute `slack = latest start - earliest start`.
6. Mark zero-slack tasks as critical.
7. During simulation, represent every task with a `CompletableFuture`; each future starts only after all dependency futures complete.

## Useful interview discussion

- Why a cycle makes the plan impossible to schedule.
- Why topological order can be valid without being unique.
- How critical-path duration differs from the sum of all task durations.
- Why virtual threads make blocking tasks cheap but do not remove the need for dependency control.
- How failures, cancellation, retries, deadlines, and resource limits could extend the simulator.
- How to persist event history without letting logging become a scheduling bottleneck.
