# RMD-402 retry policy reconciliation

This evidence note records the implementation and qualification scope for RMD-402 without replacing or compressing the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-402 requires the production retry policy to have one authoritative maximum-attempt rule, durable attempt/deadline persistence, process-death-stable retry semantics, bounded backoff/jitter, and deterministic timing tests.

## Implementation evidence

- `core/src/download.rs` exposes `DownloadPolicy.max_attempts` and bounded `retry_delay(attempt, jitter_ms)`. The delay caps exponential backoff and clamps jitter.
- `core/src/worker.rs` applies `DownloadPolicy.max_attempts` in the durable worker path. Failed work moves to `RetryWait` only when the `CoreError` is retryable and the durable attempt count remains below the configured maximum; otherwise it becomes terminal `Failed`.
- `core/src/worker.rs` persists attempt count, `retry_at_epoch_ms`, and `last_error` in `DurableDownloadSnapshot` before returning from the worker loop.
- `core/src/worker.rs::claim_eligible` treats `RetryWait` jobs as ineligible until `retry_at_epoch_ms <= now_epoch_ms`, so scheduler-visible retry timing is driven by persisted durable state rather than a transient in-memory timer.
- `core/src/retry.rs` provides a deterministic `RetryTiming` abstraction for retry planning tests, plus `execute_with_retry` for non-durable retry callers. The durable worker remains the production queue path for scheduled downloads.

## Regression coverage

- `core/src/rmd_402_retry_policy_tests.rs::worker_max_attempts_one_makes_retryable_failure_terminal` proves `DownloadPolicy.max_attempts = 1` makes even retryable HTTP 503 worker failure terminal after the initial attempt.
- `core/src/rmd_402_retry_policy_tests.rs::retry_wait_is_not_reclaimed_until_next_eligible_deadline` proves persisted future retry deadlines prevent early worker claims.
- `core/src/rmd_402_retry_policy_tests.rs::retry_wait_becomes_eligible_at_its_persisted_deadline` proves persisted retry deadlines make work eligible exactly when the durable deadline is reached and then persist the next retry attempt/deadline.
- `core/src/rmd_402_retry_policy_tests.rs::retry_wait_survives_startup_reconciliation_without_attempt_drift` proves process-death/startup reconciliation preserves a `RetryWait` snapshot without incrementing attempts, requeueing early, or clearing the persisted deadline.
- Existing `core/src/retry.rs` tests cover deterministic timing injection, bounded retry delay, cancellation before retry, max-attempt handling, and startup preservation of retry-wait state.

## Qualification policy

RMD-402 is a Rust-core/durable-store behavior and does not require Android runtime to prove the retry semantics. Per the Android qualification acceleration plan, the primary proof is Rust fmt, clippy, and workspace tests, while Android lint/build, UniFFI/APK packaging, Android smoke, and API-35 timeout lanes remain repository gates for integration drift.

## Current canonical status

The canonical TODO must remain the source of truth. Do not mark RMD-402 complete until this evidence and its exact-head qualification are merged to `master`, current `master` post-merge CI is observed, and the canonical TODO is reconciled with exact commit and run IDs.
