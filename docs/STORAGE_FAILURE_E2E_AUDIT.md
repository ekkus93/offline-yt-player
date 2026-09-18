# OYP-1903 storage failure E2E audit

The deterministic storage-failure qualification contract covers insufficient-space preflight, rejection before new writes, partial-file detection and cleanup, preservation of completed assets, and a clear user-facing recovery action.

Tests require cleanup to be scoped to incomplete data: completed assets must remain intact. The recovery surface provides both a human-readable failure reason and a concrete `Manage storage` action.

Device-capable E2E environments can inject real filesystem exhaustion while ordinary CI retains this deterministic failure contract as the minimum gate.
