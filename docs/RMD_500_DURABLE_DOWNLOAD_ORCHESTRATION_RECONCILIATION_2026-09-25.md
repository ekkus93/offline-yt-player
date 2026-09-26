# RMD-500 Durable Download Orchestration Reconciliation — 2026-09-25

This note reconciles current-master evidence for RMD-501 through RMD-508 in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. It does not change the external release gate and does not assert that the final RMD-1600 closeout is complete.

## Scope

RMD-500 covers the durable download queue as the source of truth, the real worker execution loop, pause/resume/cancel/retry controls, real progress metrics, and Android connectivity integration.

Current-master exact SHA: `517ecccd2af481ca639921ab4ec0c4b36ccf2c81`.

Qualified current-master runs:

- CI: `36214435901`
- Android smoke: `36214435877`
- Android FGS timeout: `36214435892`

## RMD-501 — Durable queue source of truth

Implementation evidence:

- `core/src/state.rs` defines the durable download state machine for queued, resolving/downloading/verifying active states, paused, retry-wait, failed, completed, and canceled states.
- `core/src/ffi.rs::download_queue()` exposes persisted durable queue snapshots through the core gateway rather than a service-local flag.
- `core/src/ffi_download_control.rs` persists queue transitions through `LibraryStore` for enqueue, pause, resume, cancel, and retry.
- `core/src/startup_reconciliation.rs` and `core/src/ffi_startup_reconciliation.rs` preserve/reconcile durable state after restart.

Behavioral evidence:

- `core/src/ffi.rs::core_service_exposes_durable_queue_and_survives_reopen`
- `core/src/ffi_startup_reconciliation.rs::ffi_startup_reconciliation_requeues_interrupted_jobs`
- `core/src/process_death_tests.rs::process_death_relaunch_reconstructs_queue_and_completes_one_fixture_item`

## RMD-502 — Worker execution loop

Implementation evidence:

- `core/src/worker.rs::DownloadWorker::claim_eligible` claims only queued work and retry-wait work whose deadline is eligible.
- `DownloadWorker::new` bounds configured concurrency through `bounded_download_concurrency`.
- `DownloadWorker::execute_one` runs the real `DownloadEngine` transfer path, stages local assets, promotes only after successful transfer/integrity handling, and persists terminal completion.
- `DownloadWorker::repair_interrupted_claims_at` repairs interrupted active claims after cancellation/process death.

Behavioral evidence:

- `core/src/worker.rs::executes_real_transfer_promotes_item_and_honors_concurrency`
- `core/src/worker.rs::retryable_failure_enters_retry_wait_with_attempt_and_error`
- `core/src/worker.rs::interrupted_claims_are_repaired_after_process_death`
- `core/src/process_death_tests.rs::process_death_relaunch_reconstructs_queue_and_completes_one_fixture_item`

## RMD-503 — Pause

Implementation evidence:

- Android UI row actions call the real gateway through `app/src/main/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicy.kt::DownloadRowControlBinding`.
- Notification/foreground actions call the same gateway through `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt`.
- `core/src/worker.rs::transfer_with_durable_stop` polls durable queue state and uses a bounded cooperative cancellation token.
- `core/src/worker_pause.rs` and `core/src/download.rs` preserve resumable partial/resume metadata according to the resume policy.

Behavioral evidence:

- `core/src/ffi_download_control.rs::pause_resume_and_cancel_persist_legal_transitions`
- `core/src/worker.rs::durable_pause_is_not_reclassified_as_terminal_cancel`
- `core/src/worker_pause.rs::paused_state_raises_cooperative_transfer_stop`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicyTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt`

## RMD-504 — Resume

Implementation evidence:

- `core/src/ffi_download_control.rs::resume` transitions paused durable work back to queued eligibility.
- `core/src/resume.rs` and `core/src/resume_http.rs` revalidate continuation metadata before appending retained partial assets.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadResumeCoordinator.kt` honors current network/Wi-Fi policy before scheduling resumed work.
- Process-death reconstruction is covered by `core/src/process_death_tests.rs`.

Behavioral evidence:

- `core/src/ffi_download_control.rs::resume_survives_reopen_and_preserves_partial_progress`
- `core/src/resume.rs` unit coverage for stored representation reuse/rejection
- `core/src/process_death_tests.rs::process_death_relaunch_reconstructs_queue_and_completes_one_fixture_item`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadResumeCoordinatorTest.kt`

## RMD-505 — Cancel

Implementation evidence:

- `core/src/worker.rs::finish_canceled` persists terminal canceled state.
- `core/src/worker_cancel_tests.rs` covers active-work cancellation through the real worker path.
- `core/src/download.rs` handles cooperative cancel and partial-retention/cleanup behavior through the transfer policy.
- UI and notification cancellation use the same control gateway path as pause/resume.

Behavioral evidence:

- `core/src/worker_cancel_tests.rs::durable_cancel_stops_active_work_and_persists_terminal_state`
- `core/src/worker_pause.rs::canceled_state_raises_same_bounded_transfer_stop`
- `core/src/ffi_download_control.rs::pause_resume_and_cancel_persist_legal_transitions`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicyTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt`

## RMD-506 — Retry

Implementation evidence:

- `core/src/ffi_download_control.rs::retry_inner` permits explicit retry only from failed state.
- Retry reuses the same durable job identity, clears only attempt/retry deadline/last error fields, and does not duplicate source/library identity.
- `core/src/worker.rs` enforces automatic retry eligibility, max attempts, and persisted retry deadlines.

Behavioral evidence:

- `core/src/ffi_download_control.rs::explicit_retry_is_failed_only_and_resets_only_attempt_error_fields`
- `core/src/ffi_download_control.rs::explicit_retry_reuses_same_durable_identity_without_duplicate_snapshot`
- `core/src/rmd_402_retry_policy_tests.rs`
- `core/src/worker.rs::retryable_failure_enters_retry_wait_with_attempt_and_error`

## RMD-507 — Real progress/speed/ETA

Implementation evidence:

- `core/src/events.rs::TransferMetricEstimator` computes real speed and ETA from a bounded sample window.
- `core/src/worker.rs::DownloadWorker` seeds, updates, emits, and persists progress from real transferred bytes.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicy.kt` preserves only trustworthy positive metrics and does not invent unknown-length progress.

Behavioral evidence:

- `core/src/worker.rs::worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer`
- `core/src/worker.rs::worker_does_not_fabricate_eta_for_unknown_length_transfer`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicyTest.kt`

Earlier focused evidence remains recorded in the TODO under PR #354 and PR #355.

## RMD-508 — Connectivity integration

Implementation evidence:

- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityObserver.kt` observes Android network capability changes and maps them to bounded app connectivity states.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadNetworkPolicy.kt` behavior is represented in `DownloadForegroundService.kt` as the policy used by download scheduling/control.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityGate.kt` pauses active work through the durable control gateway, records waiting work, enforces Wi-Fi/unmetered preference, and automatically reschedules eligible work when constraints return.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` applies persisted Wi-Fi-only settings to new schedule requests.

Behavioral evidence:

- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityMapperTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadNetworkPolicyTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityGateTest.kt`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityInstrumentationTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityInstrumentationContractTest.kt`

## Conclusion

RMD-501 through RMD-508 are implemented and qualified on exact master `517ecccd2af481ca639921ab4ec0c4b36ccf2c81` with CI, Android smoke, and Android FGS timeout passing. The canonical remediation TODO is updated only for RMD-500; remaining unchecked sections continue to be unresolved until separately implemented, qualified, reconciled, and merged.
EOF