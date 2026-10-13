# RMD-1802 — Foreground stop callback exception containment (2026-10-13)

## Production-path finding

`ForegroundWorkerOwnership.stop` and the stop-vs-begin race path invoked the generated native transfer stop callback directly. `DownloadForegroundService` calls `stop` from its Android foreground-service timeout, STOP action, and destruction callbacks. An unexpected UniFFI/SQLite exception from `DownloadWorkerExecution.requestStop` therefore escaped the framework callback, risking a process crash precisely during timeout/recovery. The existing foreground worker-thread exception wrapper does not protect these service lifecycle callbacks.

## Correction

`ForegroundWorkerOwnership.signalStop` now contains unexpected `Exception` from the native stop signal after atomically removing callback ownership. The service emits only a fixed non-sensitive warning. The native worker retains responsibility for durable settlement and startup reconciliation; a failed cooperative stop is not falsely reported as successful transfer cancellation. At-most-once stop signaling and stop-vs-begin race ownership remain intact.

## Regression and qualification

`ForegroundWorkerOwnershipTest.nativeStopGatewayExceptionCannotEscapeForegroundServiceCallback` exercises the real ownership stop path with a throwing callback, repeated stop, and rejection of new work. `throwingStopDuringBeginStopRaceIsContainedAndAtMostOnce` exercises the competing launch/stop paths and asserts no exception escapes either thread and at-most-once stop signaling.

This change requires a new passing exact-head CI matrix and relevant Android FGS-timeout qualification. RMD-1802 independent review, RMD-1803 candidate freeze, RMD-1804/1805, global closeout, and external policy/legal approval remain open.
