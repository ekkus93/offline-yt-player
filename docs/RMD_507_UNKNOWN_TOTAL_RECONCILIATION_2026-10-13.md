# RMD-507 — Unknown-length progress reconciliation (2026-10-13)

## Requirement and production proof

This evidence reconciles only the remaining RMD-507 subtask: do not fabricate numeric progress for an unknown-length response.

- Rust: `core/src/worker.rs::DownloadWorker::execute_one` tracks persisted bytes from actual transfer body chunks with CAS state checks and optional `total_bytes`; it never invents the total for an unknown-length plan. `core/src/events.rs::TransferMetricEstimator` computes speed from bounded real samples and only computes ETA from known totals.
- Rust tests: `worker_does_not_fabricate_eta_for_unknown_length_transfer`, `worker_persists_chunk_progress_before_single_asset_completion`, and `unknown_total_never_fabricates_eta`.
- Android: `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt::toDownloadsScreenState` retains a nullable percentage when `totalBytes` is absent/nonpositive. `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt::DownloadScreenPolicy.detail` prints "Progress unknown", not "0%". `DownloadRowPolicyTest.unknownLengthProgressDoesNotFabricatePercentageOrEta` covers the unknown-percentage/ETA presentation mapping.

## Qualified immutable source and CI

Implementation on `master` at `769ce8bcf918a034105a772df0945b67a8faff4c`, with all eight workflows passed:

| Workflow | Run ID | Result |
| --- | --- | --- |
| CI (Rust/JVM/UniFFI/ABI) | 38415837323 | success |
| Android smoke, Compose/goldens | 38415837330 | success |
| Android FGS timeout/UIDT | 38415837407 | success |
| Supply chain | 38415837541 | success |
| CI evidence | 38415837521 | success |
| Deterministic fixture E2E | 38415837497 | success |
| Android cold start | 38415837414 | success |
| Android real-network E2E | 38415838046 | success |

This is evidence for the specific RMD-507 subtask, **not** a frozen final candidate or a completed independent Rust/Android production review. Do not check RMD-1800 or global closeout requirements from this note. Human YouTube/service-policy/legal release approval remains entirely separate and unapproved.
