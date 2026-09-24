# RMD-500 / RMD-602 current-master evidence — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

This note records current-master evidence for the already-implemented durable download orchestration and Downloads repository paths without changing the canonical checklist by assertion. The canonical TODO remains the completion source of truth.

## Scope and fail-closed boundary

This evidence supports a later canonical reconciliation for:

- RMD-501 — durable queue source of truth.
- RMD-502 — durable worker execution loop.
- RMD-503 — pause from UI and notification through the shared gateway.
- RMD-505 — cancel from UI and notification through the shared gateway.
- RMD-506 — retry through the durable control path.
- RMD-602 — Downloads screen repository wiring, filters, and real progress/state/error rows.

This note intentionally does **not** claim completion for:

- RMD-504's network/settings-policy resume checkbox: `DownloadResumeCoordinator` covers the policy in deterministic tests, but the production resume action currently flows through `SchedulingDownloadControlGateway.resume()` rather than the coordinator.
- RMD-508 connectivity integration: the observer/gate/instrumentation slices exist, but earlier audit notes still identify production owner wiring as open.
- RMD-601, RMD-603, and RMD-604: production foundations exist, but search/detail/source-analysis state-holder/process-restoration gaps remain fail-closed.

## RMD-501 — durable queue source of truth

Current master exposes the durable queue as the production source of truth through the Rust state machine, SQLite persistence, UniFFI gateway, and Android generated gateway.

Evidence paths:

- `core/src/state.rs` defines the durable lifecycle states, including queued, active phases, paused, retry-wait, failed, canceled, and completed.
- `core/src/events.rs::DurableDownloadSnapshot` carries the queue snapshot used by the core and FFI surfaces.
- `core/src/persistence.rs` persists queue snapshots in SQLite, including state, transferred/total bytes, attempt count, retry eligibility, and structured errors.
- `core/src/ffi.rs::FfiCoreService::download_queue` exposes persisted snapshots as FFI-visible queue state.
- `core/src/ffi_download_control.rs` implements enqueue, pause, resume, cancel, and retry by loading and mutating durable snapshots rather than maintaining Android-service-local state.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` routes Android controls through `GeneratedUniffiDownloadControlGateway` using queue item IDs.

The service constants and Android policy helpers describe runtime plumbing; they do not replace the persisted queue as authoritative state.

## RMD-502 — worker execution loop

`core/src/worker.rs` contains the production durable worker loop.

Evidence paths:

- `DownloadWorker::claim_eligible` claims only eligible persisted queue work and respects the configured core concurrency bound.
- `DownloadWorker::execute_one` transitions claimed work through resolving/downloading/verifying and executes provider-neutral `DownloadPlanAsset` transfers through `DownloadEngine::transfer`.
- `ProgressCoalescer` bounds durable progress writes and emitted progress events.
- Completion is persisted only after transfer completion, verification/promotion, and completed library metadata persistence.
- `transfer_with_durable_stop` observes durable pause/cancel state at bounded cancellation points.
- `repair_interrupted_claims_at` repairs interrupted active claims after process death into retryable durable state with structured interruption errors.

Behavioral proof is in `core/src/worker.rs` tests, including real fixture transfer, concurrency limiting, retry-wait persistence, interrupted-claim repair, durable pause behavior, progress propagation, and completed-item promotion.

## RMD-503 / RMD-505 / RMD-506 — pause, cancel, and retry controls

Pause, cancel, and retry use the shared production control surface rather than independent UI or notification state.

Evidence paths:

- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` renders legal row actions from `DownloadScreenPolicy.legalActions` and dispatches them through `DownloadRowControlBinding` to `AppDownloadControlGateway`.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` delegates pause, cancel, and retry to the generated durable control gateway and shares scheduling behavior where appropriate.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` builds notification actions for pause/resume/cancel using service intents that preserve the queue item ID.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt` dispatches notification actions to the same app download-control gateway path.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt` proves pause/resume/cancel use the shared gateway and rejects missing/blank queue IDs.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGatewayTest.kt` proves scheduling/control delegation behavior.
- `core/src/ffi_download_control.rs` tests prove legal durable transitions, reopen survival, retry eligibility, and that retry resets the correct retry fields without creating duplicate identities.
- `core/src/worker.rs` tests prove pause/cancel are honored at bounded worker cancellation points and partial/resume policy is durable.

RMD-504 remains partially open because the network/settings-aware resume coordinator is not yet the production resume path.

## RMD-602 — Downloads repository wiring

The production Downloads screen is no longer hard-coded empty data. It renders durable queue state and filters/action availability from real mapped rows.

Evidence paths:

- `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private database and maps `listDownloadQueue()` to `DownloadsScreenState`.
- `AppStateRefresher` refreshes `listDownloadQueue()` off the main thread while the activity is started and publishes durable queue changes into Compose state.
- `DownloadSnapshotMapping.kt` maps durable queue snapshots to `DownloadRowModel` with state, transferred bytes, total bytes, progress percentage, speed, ETA, and error text.
- `LibraryDownloads.kt::DownloadsScreen` renders loading, unavailable, failed, empty, and ready states from `DownloadsScreenState` and derives `visibleRows` by applying `DownloadScreenPolicy.matchesFilter`.
- `DownloadScreenPolicy.Filters` exposes All, Active, Paused, Failed, and Completed filters against actual mapped durable row state.
- `DownloadsOperationalScreenTest` verifies filter behavior, action legality, and detail/progress presentation policy.
- `DownloadRowPolicyTest` verifies real progress/speed/ETA presentation and preserves unknown-length invariants.

## Qualification evidence

This note is based on current master `e6ce113b6c3cafbb89ca5f80d52bcdfceeedfa1e`, whose parent `f7fc2cfd58e813d30c8ac9466bee2e4df7df5d4e` passed post-merge CI `36064354105`, Android smoke `36064354191`, and Android FGS timeout `36064354351`. The `e6ce113b6c3cafbb89ca5f80d52bcdfceeedfa1e` post-merge master lanes started as CI `36066588204`, Android smoke `36066588016`, and Android FGS timeout `36066588038`.

A later canonical TODO reconciliation may check only the evidence-backed subtasks listed above after this note is qualified, merged, and the reconciliation branch itself passes exact-head CI. It must continue to leave the RMD-504 network/settings-policy resume checkbox, RMD-508, and the noted RMD-600 partial gaps unchecked until their production paths are complete and qualified.
