# How the scheduler works

## Follow a button click

When you take West station offline, the browser copies the scenario and sets that station's availability to false. It POSTs the scenario and the revision it edited to `/api/schedules`.

Spring deserializes JSON into typed Java records. Constructors validate station references, ID uniqueness, dates, time bounds, and priorities. The store checks the expected revision so a second browser tab cannot silently overwrite a newer plan.

The pure solver eliminates contacts on unavailable stations or in maintenance. It explores selecting or skipping each eligible contact. A contact is selectable only if no already chosen contact overlaps on either its station or satellite. The optimistic bound is the current score plus all remaining weights: if that cannot reach the best known score, the branch cannot win and is pruned.

At each complete assignment, the solver compares total priority, then count. It also computes a separate first-come baseline. The result lists every contact with either a selection or an exclusion explanation.

The store writes the next version to a temporary file in the same directory, atomically replaces the saved JSON, and only then updates memory. An IO failure leaves the previous in-memory version intact. The controller returns the updated history and the UI redraws the timeline and decision table.

## Why this algorithm?

Simple earliest-finish interval scheduling maximizes count for a single resource with unweighted intervals. Our model has weights and two resource constraints: station and satellite. Optimizing each station independently could schedule the same satellite at two stations simultaneously. The bounded exact search handles these cross-station conflicts and gives an optimum for this explicitly small problem.

The tradeoff is exponential worst-case search. The 24-contact limit is part of the product contract. Larger workloads would require a different approach such as a constraint-programming solver, stronger bounds, or a documented heuristic. Do not claim this algorithm scales to operational constellations.

## Why Java and Spring Boot?

Java records make input/output contracts explicit; immutable snapshots avoid accidentally modifying a past plan. The solver is ordinary Java without a Spring dependency, making algorithm tests independent from HTTP. Spring Boot supplies the server, JSON mapping, dependency injection, and integration-test infrastructure. Java synchronization serializes updates to the single local store; the expected revision protects against stale user edits.

Garbage collection helps manage object lifetimes but supplies neither hard real-time timing nor automatic file/socket cleanup. The file store is a conscious local-app choice, not a substitute for a database in a concurrent multiuser deployment.

## Suggested interview walkthrough

1. Explain one real constraint and show the test that enforces it.
2. Show why the baseline takes C01 and loses opportunities C02 and C03.
3. Disable West and explain the changed optimum, rather than just showing an animation.
4. Explain a cross-station satellite conflict such as C02/C05.
5. Show a saved version and explain optimistic concurrency with two browser tabs.
6. Distinguish the exact mathematical model from its real-world omissions.

## Truthful résumé bullets

- Built a Java/Spring Boot ground-station planning application that selects contact windows under antenna, satellite, maintenance, and availability constraints, with outage replanning and versioned history.
- Implemented exact priority optimization and compared it with a first-come baseline; the included 12-contact sample achieved 52 versus 38 priority points while satisfying all modeled constraints.
- Verified the solver against exhaustive search on 60 generated scenarios and added persistence, stale-update, and REST integration tests with Java 21/Linux CI.

The second bullet describes a synthetic sample, not a generalized performance or operational improvement. Do not claim NASA affiliation, real orbital predictions, PostgreSQL, WebSockets, or live spacecraft integration.
