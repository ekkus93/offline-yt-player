# RMD-500 Canonical TODO Reconciliation Plan — 2026-09-27

## Scope

This document records the exact evidence that should be used to reconcile the RMD-500 section of `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

It is intentionally not a replacement TODO. The canonical remediation TODO remains the sole completion checklist. This document exists because the current Ralph write interface commits complete UTF-8 files, and the RMD-500 canonical TODO edit must preserve the full detailed checklist without compression.

## Evidence base

- Current master at inspection time: `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`.
- RMD-500 evidence document merged by PR #386: `docs/RMD_500_DURABLE_DOWNLOAD_RECONCILIATION_2026-09-26.md`.
- PR #386 head: `4e8e821647d09c45043e4fa486857ae16c6c463e`.
- PR #386 merge commit on master: `67de1246443626d71a4ad81327dc23c3640d33a4`.
- Exact-head PR #386 qualification on `4e8e821647d09c45043e4fa486857ae16c6c463e`:
  - CI push `36265626581` and PR `36265649176` passed.
  - Android smoke push `36265626585` and PR `36265649174` passed.
  - Android FGS timeout push `36265626644` and PR `36265649173` passed.
  - Supply chain push `36265626503` and PR `36265649200` passed.
- Post-merge master qualification on `67de1246443626d71a4ad81327dc23c3640d33a4`:
  - CI `36303602321` passed.
  - Android smoke `36303602425` passed.
  - Android FGS timeout `36303602346` passed.
  - Supply chain `36303602301` passed.
- RMD-508 implementation merged by PR #387 as `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`.
- Post-merge master qualification on `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`:
  - CI `36306527110` passed.
  - Supply chain `36306527188` passed.
  - Android FGS timeout `36306527158` passed.
  - Android smoke `36306527131` passed.

## Supported canonical TODO checkbox changes

The next canonical TODO edit should mark the following RMD-500 subtasks complete, with an evidence paragraph immediately before RMD-507 or immediately after RMD-506:

### RMD-501

- `Expose durable queued/active/paused/waiting/retrying/failed/cancelled/completed states through the core gateway.`
  - Evidence: `AppCoreGateway.kt` maps generated `downloadQueue` records to `CoreDownloadSnapshot` and `CoreDownloadState` values: `QUEUED`, `RESOLVING`, `DOWNLOADING`, `PAUSED`, `RETRY_WAIT`, `FAILED`, `VERIFYING`, `COMPLETED`, and `CANCELED`.
  - Note: `RETRY_WAIT` is the durable retry/wait state. Connectivity waiting is implemented under RMD-508 using durable `PAUSED` as the constrained waiting state.
- `Ensure state survives process death.`
  - Evidence: `FfiDownloadControlService` file-backed tests persist and reopen queue state; `DownloadWorker.repair_interrupted_claims_at` repairs interrupted active claims; RMD-1202 separately proves process-death queue reconstruction.
- `Eliminate service-local booleans/constants as authoritative queue state.`
  - Evidence: production Downloads reads `GeneratedUniffiCoreGateway.listDownloadQueue()` and control paths mutate durable snapshots through `FfiDownloadControlService`; service policy constants are not the authoritative queue source for the covered control operations.

### RMD-502

- `Claim eligible durable work safely.`
- `Enforce configured concurrency.`
- `Execute the real core download plan.`
- `Emit/persist progress at bounded cadence.`
- `Commit completion only after integrity and asset promotion succeed.`
- `Release/repair claimed work after cancellation/process death.`

Evidence: `core/src/worker.rs` implements `DownloadWorker.claim_eligible`, `execute_one`, `transfer_with_durable_stop`, `finish_failed_or_retry`, and `repair_interrupted_claims_at`; tests cover fixture transfer, library promotion, bounded concurrency, retry-wait behavior, interrupted-claim repair, durable pause handling, and progress metrics.

### RMD-503

- `UI action calls real control gateway.`
- `Notification action calls same control path.`
- `Worker reaches a bounded cancellation point.`
- `Durable resumable state is persisted.`
- `Partial asset is retained only according to resume policy.`
- `Add behavioral tests.`

Evidence: UI actions are covered by `ProductionComposeBehaviorTest.downloads_actions_invoke_the_real_control_boundary`; notification actions are routed by `DownloadForegroundService` and `DownloadForegroundControlDispatcher` through `AppDownloadControlGateway`; `DownloadWorker.transfer_with_durable_stop` polls durable stop state; `DownloadEngine.transfer` retains/removes partials according to `DownloadPolicy.retain_partial_on_cancel`; control and dispatcher tests cover durable pause behavior.

### RMD-504

- `Resume transitions a paused item to eligible work.`
- `Revalidate continuation metadata before range append.`
- `Honor current network/settings policy.`
- `Add process-death + resume regression test.`

Evidence: `FfiDownloadControlService.resume` transitions `Paused` to `Queued`; `core/src/download.rs` reuses partial files only after `prepare_partial_reuse`, matching persisted validator identity, and matching `Content-Range`; tests include `resume_survives_reopen_and_preserves_partial_progress`, `resumes_existing_partial_only_with_matching_persisted_identity`, and `partial_without_identity_restarts_from_zero`. The network/settings-policy portion is supported by PR #387: `DownloadConnectivityCoordinator` applies the current download network preference before resuming durable `PAUSED` jobs only when `DownloadNetworkPolicy` returns `Allow`, and exact post-merge master qualification passed on `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`.

### RMD-505

- `Cancel stops active work.`
- `Remove/quarantine partial assets according to policy.`
- `Persist terminal cancelled state.`
- `Cancel from notification and UI uses same code path.`

Evidence: `FfiDownloadControlService.cancel` transitions durable snapshots to `Canceled`; `DownloadEngine.transfer` observes cancellation at bounded copy-loop points and removes or retains partials according to policy; notification and UI controls route through the same `AppDownloadControlGateway` boundary.

### RMD-506

- `Retry is available only for eligible failed states.`
- `Do not create duplicate library/source identities.`
- `Reset only appropriate attempt/error fields.`
- `Honor maximum-attempt/user-action semantics.`

Evidence: `FfiDownloadControlService.retry` is legal only from `Failed`, transitions the same job id to `Queued`, resets attempt/retry/error fields, preserves durable identity, and keeps automatic attempt limits in `DownloadPolicy.max_attempts`/`DownloadWorker.finish_failed_or_retry`; tests include `explicit_retry_is_failed_only_and_resets_only_attempt_error_fields` and `explicit_retry_reuses_same_durable_identity_without_duplicate_snapshot`.

## Explicitly not closed here

- RMD-1500 deterministic E2E remains unchecked. Unit/JVM/policy tests and control-boundary Compose tests do not replace the required full E2E fixture lane.
- RMD-1506 connectivity E2E remains unchecked. RMD-508 production connectivity observation/control is qualified, but full transfer-level network removal/restoration is still its own E2E task.
- RMD-1601 deterministic E2E fixture lane remains unchecked.
- RMD-1803 final full qualification remains unchecked.
