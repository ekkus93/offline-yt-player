# RMD-503 through RMD-506 download-control reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-503 through RMD-506.

## Current-master production evidence

Current master `a0f4556b5e5a13302ce2f48154d1482313977259` contains the production pause, resume, cancel, and retry control paths.

- `core/src/ffi_download_control.rs::FfiDownloadControlService` exposes `pause`, `resume`, `cancel`, and `retry` through UniFFI. Each operation loads the durable SQLite-backed snapshot, applies the core `DownloadStateMachine`, and persists the updated snapshot. There is no duplicate service-local control state.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` delegates UI/control actions to the same `AppDownloadControlGateway` used by production scheduling.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` creates notification actions for Pause, Resume, and Cancel and routes those actions through `GeneratedUniffiDownloadControlGateway` using the durable queue item id.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt` dispatches notification Pause, Resume, and Cancel to the same gateway methods used by UI code, and rejects missing queue ids instead of guessing.
- `core/src/worker_pause.rs` bridges durable `Paused` and `Canceled` state to an in-flight transfer's cooperative cancellation flag. `DownloadEngine::transfer` observes that flag before each bounded response-body read, giving the worker a bounded cancellation point.
- `core/src/download.rs` retains or discards partial files according to `DownloadPolicy.retain_partial_on_cancel`; resume reuse is not automatic from raw bytes. `prepare_partial_reuse` requires a matching persisted remote representation validator, expected range semantics, and matching URL/validator/size before appending.
- `core/src/worker.rs` distinguishes durable pause from terminal cancel after cooperative transfer stop: pause leaves the snapshot `Paused`, while cancel transitions to `Canceled`; retryable failures go to `RetryWait`, and explicit user retry is handled separately through `FfiDownloadControlService::retry`.

## RMD-503 requirement mapping — Pause

1. UI/control gateway path: `FfiDownloadControlService.pause` and `SchedulingDownloadControlGateway.pause` call the durable control gateway.
2. Notification path: `DownloadForegroundService` notification action `ACTION_PAUSE` dispatches through `DownloadForegroundControlDispatcher` to `gateway.pause(jobId)`.
3. Bounded worker stop: `propagate_durable_stop` raises the transfer stop flag when the durable snapshot becomes `Paused`, and `DownloadEngine::transfer` checks the flag before each 64 KiB response-body read.
4. Durable resumable state: the persisted snapshot remains `Paused`; partial bytes are controlled by the transfer/resume policy rather than by a service-local boolean.
5. Partial retention policy: partial retention is policy controlled by `DownloadPolicy.retain_partial_on_cancel`; unsafe append is prevented by `prepare_partial_reuse` until a matching remote validator is observed.
6. Behavioral tests: `core/src/worker_pause.rs` covers durable pause propagation, `core/src/worker.rs::durable_pause_is_not_reclassified_as_terminal_cancel` covers active-worker pause behavior, and `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt` covers notification dispatcher use of the shared gateway.

## RMD-504 requirement mapping — Resume

1. `FfiDownloadControlService.resume` transitions a paused durable snapshot back to `Queued`, making it eligible for `DownloadWorker::claim_eligible`.
2. Resume revalidation occurs in `DownloadEngine::transfer`: existing partial bytes are appendable only when `prepare_partial_reuse` validates the persisted representation against current response validators and content range semantics.
3. Current runtime network policy is applied by `SchedulingDownloadControlGateway.enqueue` for scheduling and by the durable queue worker/scheduler boundary; resume does not bypass the existing queue/scheduler policy model.
4. Process-death coverage exists in `ffi_download_control.rs::resume_survives_reopen_and_preserves_partial_progress`, proving paused state survives reopening and resumes to durable queued state while preserving progress metadata.

## RMD-505 requirement mapping — Cancel

1. UI/control gateway path: `FfiDownloadControlService.cancel` and `SchedulingDownloadControlGateway.cancel` call the durable control gateway.
2. Notification path: `DownloadForegroundService` notification action `ACTION_CANCEL` dispatches through `DownloadForegroundControlDispatcher` to `gateway.cancel(jobId)`.
3. Active work stops through the same durable-stop polling path as pause, but the worker classifies `Canceled` as terminal and persists terminal canceled state.
4. Partial-file handling is explicit and policy controlled. `worker_cancel_tests.rs::durable_cancel_stops_active_work_and_persists_terminal_state` proves no final media asset is promoted after cancel; the default policy intentionally retains a resumable partial while terminal state prevents automatic reuse unless a future explicit user action/policy chooses to recover it.
5. Durable state and UI/notification path consistency are covered by `ffi_download_control.rs`, `worker_cancel_tests.rs`, and `DownloadForegroundControlDispatcherTest`.

## RMD-506 requirement mapping — Retry

1. `FfiDownloadControlService.retry` is legal only from durable `Failed`; nonfailed work returns a typed invalid-input error.
2. Retry reuses the same durable job id and snapshot, preventing duplicate library/source identities.
3. Retry resets only attempt, retry timing, and last-error fields while preserving progress/size metadata.
4. `ffi_download_control.rs::explicit_retry_is_failed_only_and_resets_only_attempt_error_fields` and `explicit_retry_reuses_same_durable_identity_without_duplicate_snapshot` cover the retry contract.

## Qualification policy

These requirements are primarily portable durable-state, FFI-boundary, and Android gateway-dispatch behavior. Per `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`, deterministic Rust/JVM tests are the primary proof, while PR CI, Android smoke, and API-35 foreground-service timeout lanes guard integration drift. The canonical remediation TODO remains the completion source of truth and should be checked only after this evidence change passes exact-head qualification, merges to `master`, and the canonical TODO is reconciled with merged evidence.
