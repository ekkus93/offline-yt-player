# RMD-1802 — Foreground timeout must stop the native worker (2026-10-12)

## Independent production-path finding

`DownloadForegroundService.onTimeout` previously persisted the timeout and stopped foreground ownership, but the worker ran on an independent thread through `DownloadWorkerExecutor.execute` without a stop handle. `onDestroy` and explicit `ACTION_STOP` similarly released the service while a synchronous UniFFI/Rust transfer could continue. A timed-out Android 15 dataSync service therefore had no cooperative stop signal to its native transfer, and a late connectivity callback could start more work after the service was stopping. The earlier API-35 instrumentation test checked timeout-record persistence rather than a real active native transfer's stop behavior.

## Correction and qualification

`DownloadForegroundService` now owns the exact `DownloadWorkerExecution` instance and registers its `requestStop` callback in `ForegroundWorkerOwnership`. Timeout, explicit stop, and service destruction close the ownership gate and request a cooperative native platform stop. Connectivity callbacks and worker settlement cannot relaunch work after terminal shutdown. The existing Rust platform-stop path settles the original claimed attempt as retryable via durable CAS rather than treating an Android service timeout as a user cancellation.

`ForegroundWorkerOwnershipTest` executes stop-before-launch, active-stop exactly once, no overlapping writers, completed-writer isolation, and concurrent launch/stop races. `DownloadForegroundServiceTimeoutPolicyTest` asserts timeout-record persistence precedes the native stop request and foreground-service teardown. These JVM tests do not substitute for Android 15 real timeout/worker-stop instrumentation.

## Status

Implementation and deterministic tests committed to `master` subject to exact-head CI qualification. **RMD-1802, RMD-1803, RMD-1805 and global closeout remain open** until all required workflows pass on the final head, Android runtime stop behavior is qualified, and the independent review is completed. External YouTube/service-policy/legal approval is separate.
