# RMD-507 progress/speed/ETA reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-507.

## Production-path evidence

RMD-507 is implemented in the real core worker path rather than by presentation-only policy:

- `core/src/events.rs::TransferMetricEstimator` computes speed from a bounded recent sample window and only computes ETA when a meaningful total is known.
- `core/src/worker.rs::DownloadWorker` seeds the estimator from durable transferred/total byte state, updates it from real transfer results, emits bounded-cadence `CoreEvent::DownloadProgress` events, and avoids duplicate final metric samples.
- Known-length worker transfers propagate transferred bytes, total bytes, speed, and completion ETA.
- Unknown-length worker transfers propagate real transferred bytes and speed while preserving unknown total/ETA, so numeric percentage/ETA is not fabricated.
- Android `DownloadProgressPresentationMapper` and `DownloadRowPresentation` preserve the unknown-length invariant and only expose trustworthy positive speed/meaningful ETA values.

Deterministic behavioral coverage includes `worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer`, `worker_does_not_fabricate_eta_for_unknown_length_transfer`, `DownloadRowPolicyTest.unknownLengthProgressDoesNotFabricatePercentageOrEta`, and `DownloadRowPolicyTest.progressMapperKeepsOnlyRealPositiveMetrics`.

## Exact-head qualification and merge

PR #354 exact implementation head `4b881b06d60540b2a8c7e15e3dfdb1cb63b54f63` passed all six discovered exact-head runs before merge:

- push CI `35983598837`
- push Android smoke `35983599037`
- push Android FGS timeout `35983599025`
- PR CI `35984323724`
- PR Android smoke `35984323707`
- PR Android FGS timeout `35984323690`

PR #354 was squash-merged to `master` as `72b2af7a85576250655eb10fdd08de48923fec46`.

Post-merge master qualification was started automatically as CI `35986175688`, Android smoke `35986175695`, and Android FGS timeout `35986175679`; those runs must be terminal green before the canonical RMD-507 checkboxes are reconciled complete.
