# RMD-507 progress/speed/ETA implementation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-507.

## Implementation

RMD-507 is repaired in the production core worker path rather than by UI-only formatting policy.

- `core/src/events.rs::TransferMetricEstimator` calculates bounded-window transfer speed and emits ETA only when total bytes are known.
- `core/src/worker.rs::DownloadWorkerReport` now carries `CoreEvent::DownloadProgress` events emitted by the real `DownloadWorker` execution path.
- `DownloadWorker::execute_one` seeds the estimator from the durable snapshot, updates transferred bytes after real `DownloadEngine::transfer` results, coalesces progress emissions, and sends a final progress event at completion.
- Known-length transfers propagate `bytes_downloaded`, `total_bytes`, positive speed, and completion ETA `0`.
- Unknown-length transfers propagate real transferred bytes and speed while preserving `total_bytes = None` and `eta_seconds = None`, so no numeric percent or ETA is fabricated.

## Behavioral proof

Deterministic Rust fixture tests exercise the production worker against loopback transfers:

- `worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer`
- `worker_does_not_fabricate_eta_for_unknown_length_transfer`

Existing UI policy coverage remains relevant for presentation safety:

- `DownloadRowPolicyTest.unknownLengthProgressDoesNotFabricatePercentageOrEta`
- `DownloadRowPolicyTest.progressMapperKeepsOnlyRealPositiveMetrics`

## Qualification policy

The primary proof is deterministic Rust/core behavior because RMD-507 is portable worker orchestration and presentation-policy mapping, matching the Android qualification acceleration plan's instruction to move non-runtime proof out of emulator lanes. Android smoke and API-35 timeout lanes remain required exact-head integration gates before merge.
