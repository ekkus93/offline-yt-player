# RMD-400 retry reconciliation

This note records precise implementation and qualification evidence for the completed retry-focused RMD-400 work currently merged to `master`. It does not replace the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; the canonical TODO must still be reconciled after this evidence note is qualified and merged.

## Scope

This note covers:

- RMD-401 — make retryability authoritative.
- RMD-402 — unify retry policy with production scheduler.

It does not close unrelated RMD-400 items: RMD-403 network-read I/O mapping, RMD-404 deletion asset removal, RMD-405 hash-based corruption detection, RMD-406 diagnostic redaction, RMD-407 quality ranking, or RMD-408 concurrency policy reconciliation.

## RMD-401 evidence — retryability authority

Implementation and regression coverage:

- `core/src/retry.rs::execute_with_retry` treats `CoreError.retryable` as authoritative. A nonretryable error is not retried merely because its broad `ErrorKind` is normally transient.
- `core/src/worker.rs::execute_ready_at` applies the same authority in the durable worker path by moving failed work to `RetryWait` only when the error is retryable and another attempt remains.
- `core/src/rmd_401_retry_authority_tests.rs` proves the production durable worker treats HTTP 404 as terminal `Failed`, treats HTTP 503 as retryable `RetryWait`, and never promotes nonretryable `SourceChanged` errors due only to `ErrorKind`.
- Existing `core/src/retry.rs` tests independently cover generic retry behavior, nonretryable HTTP status, nonretryable source-change, retryable transient network/HTTP failures, permanent failures, max-attempt stopping, and cancellation.

Qualification evidence:

- PR #339 exact head `9c26e26a18b71ad0afb0760f6e415c86242b1f19` passed PR CI `35890573106`, PR Android smoke `35890573413`, PR API-35 FGS timeout `35890573105`, push CI `35890566150`, push Android smoke `35890566155`, and push API-35 FGS timeout `35890566208`.
- PR #339 merged as `59ad0f36d6c2f878ad930849a2c8487c6198ce00`.
- Post-merge `master` at `59ad0f36d6c2f878ad930849a2c8487c6198ce00` passed CI `35892659868`, Android smoke `35892659977`, and API-35 FGS timeout `35892659883`.

## RMD-402 evidence — durable retry policy and scheduler semantics

Implementation and regression coverage:

- `core/src/download.rs::DownloadPolicy.max_attempts` is consumed by the production durable worker retry decision rather than being a decorative policy value.
- `core/src/download.rs::retry_delay` bounds exponential backoff and clamps jitter.
- `core/src/worker.rs::finish_failed_or_retry` persists terminal failed vs. retry-wait state, attempt count, retry deadline, and typed error into `DurableDownloadSnapshot` before returning.
- `core/src/worker.rs::claim_eligible` makes `RetryWait` work eligible only when the persisted `retry_at_epoch_ms` is absent or has reached the current worker clock.
- `core/src/retry.rs::RetryTiming` provides deterministic timing for retry-planning tests.
- `core/src/rmd_402_retry_policy_tests.rs` proves max-attempt-one terminal behavior, future retry deadlines preventing early claims, retry eligibility at the persisted deadline, and startup reconciliation preserving `RetryWait` without attempt/deadline drift.

Qualification evidence:

- PR #340 first exact head `2726f81311037e049cb8b9440c8240fb82b9ba32` passed PR CI `35893142842`, PR Android smoke `35893142904`, PR API-35 FGS timeout `35893142820`, push CI `35893125105`, and push Android smoke `35893125156`. The duplicate push API-35 run `35893125044` failed in existing timeout instrumentation, so the branch was advanced with this evidence note to force a fresh exact-head qualification.
- PR #340 final exact head `44b49855392e1b921a09445e8ac385edf7bcdd3c` passed PR CI `35911803407`, PR Android smoke `35911803588`, PR API-35 FGS timeout `35911803427`, push CI `35911799838`, push Android smoke `35911799845`, and push API-35 FGS timeout `35911800022`.
- PR #340 merged as `11e2020268d0eb35ed59d341cd649c80e56534f5`.
- Post-merge `master` at `11e2020268d0eb35ed59d341cd649c80e56534f5` passed CI `35913786091`, Android smoke `35913786133`, and API-35 FGS timeout `35913786001`.

## Reconciliation qualification note

The first push CI for this reconciliation branch (`35916543334`) encountered a runner/environment TLS certificate verification failure during `actions/checkout` in the Android lint/unit-build job before repository checkout completed. The equivalent pull-request CI on the same exact SHA passed. A later exact head `11eba36157c13a36ed01dc45e07c2c1157fbbebc` passed push CI `35917502192`, Android smoke `35917502268`, and API-35 FGS timeout `35917502234`; its duplicate PR CI `35917507880` failed only because Maven Central returned HTTP 403 while resolving Kotlin buildscript artifacts, while PR Android smoke `35917507935` and PR API-35 FGS timeout `35917507856` passed. This documentation-only follow-up advances the branch again so the transient dependency-resolution failure can be requalified on a fresh exact head.

## Current canonical status

RMD-401 and RMD-402 now have production-path implementation and exact-head/post-merge qualification evidence. The canonical TODO should be reconciled in a later detailed-TODO update that preserves the file's full checklist structure and cites the evidence above. This evidence note alone must not be treated as canonical checkbox completion.
