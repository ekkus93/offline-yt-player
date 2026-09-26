# RMD-500 Durable Download Orchestration Partial Reconciliation — 2026-09-25

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

This reconciliation intentionally checks only the durable orchestration subtasks that are already implemented, behaviorally covered, merged to `master`, and qualified by exact-head CI. It preserves the remaining fail-closed TODO state for production network/settings-aware resume and RMD-508 connectivity production-owner wiring.

## Qualified base

Current-master exact SHA used for this reconciliation: `517ecccd2af481ca639921ab4ec0c4b36ccf2c81`.

Qualified master runs on that exact SHA:

- CI: `36214435901`
- Android smoke: `36214435877`
- Android FGS timeout: `36214435892`

The supporting current-master evidence note is `docs/RMD_500_602_CURRENT_MASTER_EVIDENCE_2026-09-24.md`.

## Checked in this reconciliation

### RMD-501 — Durable queue source of truth

Evidence paths:

- `core/src/state.rs` defines the durable lifecycle states, including queued, active phases, paused, retry-wait, failed, canceled, and completed.
- `core/src/events.rs::DurableDownloadSnapshot` carries the queue snapshot used by the core and FFI surfaces.
- `core/src/persistence.rs` persists queue snapshots in SQLite, including state, transferred/total bytes, attempt count, retry eligibility, and structured errors.
- `core/src/ffi.rs::FfiCoreService::download_queue` exposes persisted snapshots as FFI-visible queue state.
- `core/src/ffi_download_control.rs` implements enqueue, pause, resume, cancel, and retry by loading and mutating durable snapshots rather than maintaining Android-service-local state.

Behavioral evidence includes durable queue reopen/state exposure and process-death reconstruction tests.

### RMD-502 — Worker execution loop

Evidence paths:

- `core/src/worker.rs::DownloadWorker::claim_eligible` claims only eligible persisted queue work and respects the configured concurrency bound.
- `DownloadWorker::execute_one` transitions claimed work through resolving/downloading/verifying and executes provider-neutral `DownloadPlanAsset` transfers through `DownloadEngine::transfer`.
- `ProgressCoalescer` bounds durable progress writes and emitted progress events.
- Completion is persisted only after transfer completion, verification/promotion, and completed library metadata persistence.
- `transfer_with_durable_stop` observes durable pause/cancel state at bounded cancellation points.
- `repair_interrupted_claims_at` repairs interrupted active claims after process death into retryable durable state with structured interruption errors.

Behavioral evidence includes real fixture transfer, concurrency limiting, retry-wait persistence, interrupted-claim repair, durable pause behavior, progress propagation, and completed-item promotion.

### RMD-503 — Pause

Evidence paths:

- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` renders legal row actions and dispatches through `DownloadRowControlBinding` to `AppDownloadControlGateway`.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` delegates pause to the generated durable control gateway.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` builds notification actions with the durable queue item ID.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt` dispatches notification pause/resume/cancel actions to the same app download-control gateway path.
- `core/src/worker.rs` and `core/src/worker_pause.rs` persist resumable state and reach bounded cancellation points.

Behavioral evidence includes `DownloadForegroundControlDispatcherTest`, `SchedulingDownloadControlGatewayTest`, `core/src/ffi_download_control.rs` control-transition tests, and worker pause/cancel tests.

### RMD-504 — Resume, partial

Checked evidence-backed subtasks:

- Resume transitions a paused item to eligible work.
- Continuation metadata is revalidated before retained partials are reused/appended.
- Process-death plus resume behavior is covered by deterministic core regression tests.

The current network/settings-policy production path remains unchecked. `docs/RMD_500_602_CURRENT_MASTER_EVIDENCE_2026-09-24.md` records that `DownloadResumeCoordinator` covers the policy in deterministic tests, but the production resume action currently flows through `SchedulingDownloadControlGateway.resume()` rather than the coordinator.

### RMD-505 — Cancel

Evidence paths:

- UI and notification cancellation use the same control-gateway path as pause/resume.
- `core/src/ffi_download_control.rs` persists terminal canceled state for legal cancel transitions.
- `core/src/worker.rs` and `core/src/worker_cancel_tests.rs` cover active-work cancellation through the real worker path and bounded transfer stops.
- Partial asset behavior is governed by the same durable transfer policy used by the worker.

### RMD-506 — Retry

Evidence paths:

- `core/src/ffi_download_control.rs::retry_inner` permits explicit retry only from failed state.
- Retry reuses the same durable job identity, clears only attempt/retry deadline/last error fields, and does not duplicate source/library identity.
- `core/src/worker.rs` enforces automatic retry eligibility, max attempts, and persisted retry deadlines.

Behavioral evidence includes failed-only retry tests, same-identity retry tests, retry policy tests, and worker retry-wait tests.

## Preserved unchecked items

### RMD-504 network/settings-policy resume

The checkbox `Honor current network/settings policy` remains unchecked until the production resume action is wired through the policy-aware resume/scheduling path and qualified with exact-head CI.

### RMD-508 connectivity integration

RMD-508 remains unchecked. Current `master` contains observer/gate/instrumentation slices, but the existing current-master evidence note explicitly keeps RMD-508 fail-closed because prior audit notes still identify production owner wiring as open. This reconciliation does not claim completion for RMD-508.

## Conclusion

RMD-501, RMD-502, RMD-503, RMD-505, and RMD-506 are reconciled as complete. RMD-504 is partially reconciled with its network/settings-policy checkbox left open. RMD-507 remains complete under its previously recorded evidence. RMD-508 remains fully open.
