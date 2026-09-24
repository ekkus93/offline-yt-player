# RMD-502 durable worker-loop reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-502.

## Current-master production evidence

Current master `81c6635acc42407e2cc6b60cf4c56ead3728c0e7` contains the production worker loop in `core/src/worker.rs`.

- `DownloadWorker::claim_eligible` loads durable snapshots, claims only queued or retry-eligible work, transitions the claim to `Resolving`, increments the durable attempt counter, persists the claim, and stops at the core-bounded `max_concurrent` limit.
- `DownloadWorker::execute_one` transitions claimed work to `Downloading`, executes each real provider-neutral `DownloadPlanAsset` through `DownloadEngine::transfer`, stages resulting local assets, persists bounded/coalesced progress, enters `Verifying`, promotes the verified completed item transactionally, and only then persists `Completed`.
- `ProgressCoalescer` is used before durable progress writes, avoiding an unbounded write/event cadence.
- `transfer_with_durable_stop` cooperatively observes external cancellation and durable pause/cancel state while a transfer is running.
- `repair_interrupted_claims_at` repairs process-death claims in `Resolving`, `Downloading`, or `Verifying` into immediately eligible `RetryWait` durable state with a structured retryable interruption error.
- Retryable execution failures persist `RetryWait`, attempt count, next eligible retry time, and structured error; nonretryable/exhausted work persists `Failed`; cancellation persists `Canceled` unless the durable control path identifies the stop as Pause.

## Behavioral proof

Deterministic Rust tests in `core/src/worker.rs` exercise the production worker path against a loopback HTTP fixture. They prove real transfer and final asset promotion, concurrency limiting, retry-wait persistence, interrupted-claim repair after process death, and durable pause behavior. Completion assertions verify the final file exists, staging metadata is cleared, the library item is completed, and the durable queue snapshot is `Completed` only after successful promotion.

## Requirement mapping

1. Eligible durable work is claimed through persisted state transitions.
2. Concurrency is enforced by the core-bounded worker limit.
3. The real `DownloadPlan`/`DownloadEngine` transfer path executes.
4. Progress is persisted at bounded cadence through `ProgressCoalescer`.
5. Completion is committed only after transfer integrity, staging, verification transition, and library promotion succeed.
6. Cancellation is persisted and interrupted active claims are repaired into retryable durable state after process death.

## Qualification policy

These requirements are portable-core orchestration behavior, so deterministic Rust fixture tests are the primary proof under the Android qualification acceleration plan; Android smoke remains integration evidence. The canonical TODO remains the source of completion truth and should be checked only after this evidence change passes exact-head qualification, merges to `master`, and the canonical TODO is reconciled with merged evidence.
