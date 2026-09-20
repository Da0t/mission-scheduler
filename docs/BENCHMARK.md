# Local demo benchmark

Scenario: 12 fixed contact windows, 3 stations, 2 maintenance windows. 50 warmup solves, then 200 measured solves.

Environment: Mac OS X / aarch64, Java 24.0.1.

Exact solver: 52 priority, 8 contacts. First-come baseline: 38 priority, 7 contacts.

Measured solver median: 28 microseconds; p95: 35 microseconds. This small synthetic benchmark is not a scalability or production latency guarantee. The exact search is exponential in the worst case and accepts at most 24 contacts.
