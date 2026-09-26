# RMD-500 Durable Download Orchestration Reconciliation — 2026-09-26

## Scope

This document records current-master evidence for the RMD-500 durable download orchestration tranche in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

It is an evidence document only. The canonical TODO remains the source of truth for checkbox state. Individual RMD-500 checkboxes should be updated only after this evidence branch is qualified, merged to `master`, and the exact-head/post-merge CI evidence is available.

## Exact evidence base

- Audited master: `9a03c0567e0b6554353a95ebc450997a21538cec`
- Master commit message: `docs: reconcile RMD-1403 through RMD-1405 (#385)`
- Required strategy document reloaded from the same SHA: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`
- Canonical TODO reloaded from the same SHA: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`

## Evidence summary

### RMD-501 — Durable queue state source of truth

Current master exposes durable queued/active/paused/retrying/failed/cancelled/completed queue state through both Rust and Android gateway boundaries:

- `core/src/ffi_download_control.rs` persists `DurableDownloadSnapshot` records through `FfiDownloadControlService.enqueue`, `pause`, `resume`, `cancel`, and `retry`.
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` maps generated `downloadQueue` records into `CoreDownloadSnapshot` and the full `CoreDownloadState` enum: `QUEUED`, `RESOLVING`, `DOWNLOADING`, `PAUSED`, `RETRY_WAIT`, `FAILED`, `VERIFYING`, `COMPLETED`, and `CANCELED`.
- `MainActivity.bootstrapProductionUi` reads `GeneratedUniffiCoreGateway.listDownloadQueue()` into `DownloadsScreenState` rather than using hard-coded production Downloads rows.
- `FfiDownloadControlService` tests prove queued state is persisted, pause/resume/cancel transitions persist, resume survives database reopen, explicit retry preserves the same durable identity, and missing/illegal operations return typed errors.

This covers the durable-state and process-survival portions of RMD-501. It also establishes that service-local booleans are not the authoritative queue state for the covered control operations.

### RMD-502 — Worker execution loop

Current master contains a real durable worker loop in `core/src/worker.rs`:

- `DownloadWorker.claim_eligible` claims `Queued` and due `RetryWait` work up to the bounded concurrency limit.
- `DownloadWorker.execute_one` transitions `Resolving -> Downloading -> Verifying -> Completed`, executes `DownloadEngine` transfers for each `DownloadPlanAsset`, stages assets, promotes a completed `LibraryItem`, and persists the completed snapshot only after asset promotion.
- `DownloadWorker.repair_interrupted_claims_at` repairs interrupted `Resolving`, `Downloading`, and `Verifying` snapshots after process death.
- `DownloadWorkerReport` records claimed, completed, paused, failed, retry-wait, canceled, repaired, and progress outcomes.
- Tests in `core/src/worker.rs` prove real fixture transfer, library promotion, bounded concurrency, retry-wait behavior, interrupted-claim repair, and durable pause handling.

This is production-path Rust evidence for the worker execution loop. Android runtime worker scheduling evidence remains tied to the scheduler/service paths below and to the final deterministic E2E lane.

### RMD-503 through RMD-506 — Pause, Resume, Cancel, Retry controls

Current master exposes a shared durable control path:

- Rust core control service: `core/src/ffi_download_control.rs`
  - `pause(job_id)` transitions the durable snapshot to `Paused`.
  - `resume(job_id)` transitions paused work back to `Queued`, preserving partial progress fields for continuation revalidation.
  - `cancel(job_id)` transitions the durable snapshot to `Canceled`.
  - `retry(job_id)` is accepted only from `Failed`, resets attempt/timing/error fields, preserves durable identity, and does not duplicate snapshots.
- Android generated gateway: `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppDownloadControlGateway.kt`
  - `GeneratedUniffiDownloadControlGateway` reflects the generated `FfiDownloadControlService` methods off the main thread and returns typed `CoreGatewayResult<Boolean>` values.
- Android scheduler wrapper: `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt`
  - `enqueue` persists through the delegate then schedules the same durable queue item through `DownloadExecutionScheduler`, honoring current download settings.
  - `pause`, `resume`, `cancel`, and `retry` delegate to the same production control gateway.
- Notification path: `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt`
  - notification actions are `ACTION_PAUSE`, `ACTION_RESUME`, and `ACTION_CANCEL`.
  - `dispatchControlAction` opens `GeneratedUniffiDownloadControlGateway` against the app database.
  - `DownloadForegroundControlDispatcher` sends notification actions through the same `AppDownloadControlGateway` methods.
- Notification tests: `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt`
  - proves pause/resume/cancel notification actions use the shared gateway path and reject missing queue item IDs instead of guessing.
- Compose behavioral tests: `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`
  - `downloads_actions_invoke_the_real_control_boundary` proves Downloads UI actions call the `AppDownloadControlGateway` boundary.
  - `add_analyze_setup_and_download_use_gateway_boundaries` proves Download Setup uses source analysis and download-control boundaries for analyze/setup/enqueue.

This evidence covers the shared durable control paths for RMD-503 through RMD-506. The remaining final proof should come from the RMD-1500 deterministic E2E fixture lane, because policy/unit tests cannot be cited as full E2E evidence.

### RMD-507 — Real progress/speed/ETA

RMD-507 is already checked in the canonical TODO. Current evidence remains valid:

- `core/src/events.rs::TransferMetricEstimator` computes bounded-window speed and ETA only when total size is meaningful.
- `core/src/worker.rs::DownloadWorker` emits real byte progress from transfers and avoids fabricating ETA for unknown-length responses.
- Android presentation mapping preserves unknown-length progress without fabricated percentage/ETA.

### RMD-508 — Connectivity integration

Current master contains policy/scheduler pieces but this document does not claim full RMD-508 completion:

- `DownloadNetworkPolicy` models AnyNetwork/WifiOnly decisions for None/Metered/Unmetered connectivity.
- `SchedulingDownloadControlGateway` schedules queued work with the current `wifiOnlyDownloads` setting.
- `DownloadExecutionScheduler` supplies Android runtime scheduling paths and constraints.

The full RMD-508 checkbox set requires observation of Android network capability changes, waiting/pause state transitions, automatic eligibility on restored constraints, and instrumentation coverage. Those should remain unchecked until implemented and qualified.

## Recommended canonical TODO reconciliation after qualification

After this evidence branch is qualified and merged, reconcile only the supported RMD-500 items:

- RMD-501: likely eligible for completion if exact-head CI confirms current production paths.
- RMD-502: likely eligible for completion for the Rust/core durable worker loop; final app-pipeline E2E remains RMD-1500.
- RMD-503/RMD-504/RMD-505/RMD-506: likely eligible for durable control-path completion with UI/notification/shared-gateway evidence, while final end-to-end pause/resume/cancel notification proof remains RMD-1507.
- RMD-507: already complete.
- RMD-508: keep unchecked until runtime network-observation and instrumentation evidence exist.

Do not use this document to close RMD-1500, RMD-1601 deterministic E2E, or RMD-1803 final full qualification.
