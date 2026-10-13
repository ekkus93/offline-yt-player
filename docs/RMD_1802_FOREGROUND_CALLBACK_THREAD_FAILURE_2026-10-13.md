# RMD-1802 — Foreground callback-thread failure containment (2026-10-13)

## Finding

`DownloadForegroundService` dispatches real Android connectivity changes and durable-worker settlement on plain Java threads. The callbacks call generated UniFFI/SQLite gateways. A database reset, late teardown, or unexpected gateway failure can throw, and an exception escaping any plain thread can terminate the entire Android process. The earlier `currentDownloadState` catch protects one gateway read but not `runConnectivityCoordinator` or `settleWorkerAfterExecution`.

## Correction

`DownloadForegroundService.kt` uses a shared guarded task launcher for observer reuse, connectivity callbacks, and worker execution/settlement. The wrapper catches unexpected `Exception` at the thread boundary, logs a fixed warning without provider metadata, and leaves durable recovery to the startup reconciliation path. Existing worker `finally` still releases native ownership and clears the worker-running flag. No platform stop/cancel policy is changed.

`DownloadForegroundServiceTaskSafetyTest.kt` verifies that an exception does not escape the callback boundary and produces one failure signal, while success produces none.

## Qualification

Source and host-JVM test added on `master`; exact-head CI and packaged Android smoke/FGS qualification remain pending. RMD-1802/1803/1805 and external service/legal release approval remain unchecked.
