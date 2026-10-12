# RMD-1802 — Android UIDT platform-stop and native-worker ownership (2026-10-11)

## Release-blocking independent review finding

The production `DownloadUserInitiatedJobService` in `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt` starts `DownloadWorkerExecutor.execute(...)` on a standalone thread and returns `true`. Its `onStopJob(params)` unconditionally returns `true` without notifying or stopping that thread. `runAndSettleDownloadJob` subsequently invokes `jobFinished(params, false)` unconditionally on worker exit, even if `onStopJob` has already removed ownership.

Android's documented `JobService.onStopJob` contract requires the application to stop ongoing work regardless of whether it requests rescheduling; after that callback, `jobFinished` is no longer required. The service therefore risks continuing a transfer after JobScheduler revokes its network constraint or UIDT execution entitlement, and can settle stale job parameters. The existing `DownloadUidtExecutionInstrumentedTest` proves ordinary completion on API 35 but does not exercise the platform-stop callback.

Android API reference: https://developer.android.com/reference/android/app/job/JobService

## Required production correction

1. Maintain per-job active execution/stop identity, rather than a detached untracked transfer thread. Stop/finish must settle an exact JobScheduler attempt at most once. Never call `jobFinished` for an attempt already stopped by the platform.
2. Provide cooperative cancellation into the *running* native Rust worker. Interrupting only the Java thread is not enough because the synchronous UniFFI transfer runs in Rust. An OS stop must not be interpreted as user Cancel or change a user-paused job into an automatically resumable job.
3. Persist an explicit retryable platform-stop state using the durable queue's exact-state/attempt CAS facilities, and ensure the native transfer poll recognizes that stop *for the original claim*. A subsequent worker must not reuse the same files while the previous attempt is still writing.
4. Respect stop reasons, particularly user-requested UIDT app stopping where Android will not honor automatic rescheduling. Preserve the explicit user Pause/Cancel/Completed states, and do not invent completed transfer state.
5. On legal JobScheduler rescheduling, reenter the same durable worker/asset/verification pipeline; do not sidestep library integrity or storage guards.
6. Do not perform expensive blocking database/native transfer work on the `JobService` main callback thread; provide a bounded stop signal and durable transition with a fail-closed fallback.

## Qualification required before closing RMD-1802

- Deterministic Rust tests: stop-before-claim; stop-after-claim; stop-during-chunk; stop racing user pause/cancel, completion, and a reclaimed retry attempt; no terminal canceled state from platform-only stop; no concurrent mutation/promotion by stale worker.
- JVM tests for exactly-once JobScheduler settlement, suppression after stop, stop-reason handling, and stopped-job identity isolation.
- Real API-35 host-driven instrumentation that revokes UIDT execution or required network while a fixture transfer is active, verifies the transfer stops, verifies durable resumability/user-stop behavior, and exercises a legal reschedule without duplicate workers or asset corruption.
- Exact-head main CI, smoke, FGS/UIDT, cold-start, real-network E2E, supply-chain, evidence and deterministic fixture workflows. Keep live provider smoke separate.

## Status

**OPEN — source correction and stop/restart tests not yet implemented.** Do not interpret the existing normal-completion UIDT test as platform stop/restart acceptance. This is not a hardware, credentials, or user-decision blocker; continue implementation autonomously. Keep the canonical review, qualification, and engineering closeout checkboxes unchecked.

Current work: platform-stop implementation is pending full exact-head qualification; do not close RMD-1802.
