# RMD-500 Durable Download Orchestration Reconciliation — 2026-09-25

This note repairs the RMD-500 evidence trail without changing unrelated remediation sections. It records the current production evidence for durable queue orchestration and the additional production resume wiring added on the RMD-500 reconciliation branch. The canonical completion source remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

This reconciliation supports RMD-501 through RMD-507 after exact-head qualification and merge. It intentionally does not close RMD-508 because current-master audit notes still identify long-lived production connectivity-owner wiring as separate work.

Current master before this branch: `517ecccd2af481ca639921ab4ec0c4b36ccf2c81`.

Already-qualified current-master runs:

- CI: `36214435901`
- Android smoke: `36214435877`
- Android FGS timeout: `36214435892`

## RMD-501 — Durable queue source of truth

Implementation evidence:

- `core/src/state.rs` defines the durable download state machine for queued, active, paused, retry-wait, failed, completed, and canceled states.
- `core/src/ffi.rs::download_queue()` exposes persisted durable queue snapshots through the core gateway rather than a service-local flag.
- `core/src/ffi_download_control.rs` persists queue transitions through `LibraryStore` for enqueue, pause, resume, cancel, and retry.
- `core/src/startup_reconciliation.rs` and `core/src/ffi_startup_reconciliation.rs` preserve and reconcile durable state after restart.

Behavioral evidence includes durable queue exposure/reopen tests, startup-reconciliation tests, and the process-death fixture in `core/src/process_death_tests.rs`.

## RMD-502 — Worker execution loop

Implementation evidence:

- `core/src/worker.rs::DownloadWorker::claim_eligible` claims only queued work and retry-wait work whose deadline is eligible.
- `DownloadWorker::new` bounds configured concurrency through `bounded_download_concurrency`.
- `DownloadWorker::execute_one` runs the real `DownloadEngine` transfer path, stages local assets, promotes only after successful transfer/integrity handling, and persists terminal completion.
- `DownloadWorker::repair_interrupted_claims_at` repairs interrupted active claims after cancellation/process death.

Behavioral evidence includes worker tests for real fixture transfer, promotion, concurrency, retry-wait persistence, interrupted-claim repair, and process-death completion.

## RMD-503 — Pause

Implementation evidence:

- Android UI row actions call the real gateway through `app/src/main/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicy.kt::DownloadRowControlBinding`.
- Notification/foreground actions call the same gateway through `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt`.
- `core/src/worker.rs::transfer_with_durable_stop` polls durable queue state and uses a bounded cooperative cancellation token.
- `core/src/worker_pause.rs` and `core/src/download.rs` preserve resumable partial/resume metadata according to the resume policy.

Behavioral evidence includes FFI transition tests, active-worker pause tests, worker pause propagation tests, UI row policy tests, and foreground dispatcher tests.

## RMD-504 — Resume

Implementation evidence:

- `core/src/ffi_download_control.rs::resume` transitions paused durable work back to queued eligibility.
- `core/src/resume.rs` and `core/src/resume_http.rs` revalidate continuation metadata before appending retained partial assets.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` now routes production resume through `DownloadResumeCoordinator` instead of directly delegating to the generated control gateway.
- `MainActivity` maintains an `AndroidDownloadConnectivityObserver` snapshot and passes it into `SchedulingDownloadControlGateway`, so resume honors the current Wi-Fi-only/download-network setting before durable queue mutation and scheduling.
- Process-death reconstruction is covered by the core process-death fixture.

Behavioral evidence includes FFI resume reopen tests, resume representation tests, process-death tests, `DownloadResumeCoordinatorTest`, and `SchedulingDownloadControlGatewayTest` coverage proving that production resume waits without queue mutation when Wi-Fi-only settings conflict with current connectivity and schedules resumed work only when current policy allows it.

## RMD-505 — Cancel

Implementation evidence:

- `core/src/worker.rs::finish_canceled` persists terminal canceled state.
- `core/src/worker_cancel_tests.rs` covers active-work cancellation through the real worker path.
- `core/src/download.rs` handles cooperative cancel and partial-retention/cleanup behavior through transfer policy.
- UI and notification cancellation use the same control gateway path as pause/resume.

Behavioral evidence includes worker cancel tests, worker pause/cancel stop propagation, FFI legal-transition tests, UI row policy tests, and foreground dispatcher tests.

## RMD-506 — Retry

Implementation evidence:

- `core/src/ffi_download_control.rs::retry_inner` permits explicit retry only from failed state.
- Retry reuses the same durable job identity, clears only attempt/retry deadline/last error fields, and does not duplicate source/library identity.
- `core/src/worker.rs` enforces automatic retry eligibility, max attempts, and persisted retry deadlines.

Behavioral evidence includes explicit failed-only retry tests, no-duplicate identity tests, retry policy tests, and worker retry-wait tests.

## RMD-507 — Real progress/speed/ETA

Implementation evidence:

- `core/src/events.rs::TransferMetricEstimator` computes real speed and ETA from a bounded sample window.
- `core/src/worker.rs::DownloadWorker` seeds, updates, emits, and persists progress from real transferred bytes.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicy.kt` preserves only trustworthy positive metrics and does not invent unknown-length progress.

Behavioral evidence and exact PR run IDs remain recorded in the canonical TODO under RMD-507.

## RMD-508 remains open

Current master contains useful RMD-508 pieces: `AndroidDownloadConnectivityObserver`, `DownloadConnectivityGate`, connectivity mapper/policy tests, and device-side instrumentation. However `docs/RMD_508_CONNECTIVITY_GATE_AUDIT.md` still records the remaining production-owner wiring requirement, so this reconciliation does not mark RMD-508 complete.
