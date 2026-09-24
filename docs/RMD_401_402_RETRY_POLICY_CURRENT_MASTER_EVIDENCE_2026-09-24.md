# RMD-401/RMD-402 retry policy current-master evidence — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-400.

This note records current-master evidence for the retry-authority and retry-policy portions of RMD-400. It does not mark the canonical TODO complete by itself; the canonical checklist remains the source of truth and still requires a later checkbox reconciliation PR after exact-head qualification.

## Current master baseline

The current master baseline for this evidence is `601654bf7bf063f014bb8ba5fe7fa368a77a00be`. The only change after the previously qualified RMD-203 master SHA is a documentation-only RMD-200 reconciliation-intent note, so the retry production paths and tests described here are inherited from the already-qualified code state.

## RMD-401 — retryability is authoritative

`core/src/retry.rs::execute_with_retry` makes the explicit `CoreError.retryable` flag authoritative together with broad error classification:

- `is_retryable(error)` requires `error.retryable` to be true.
- It also requires `classify_error(&error.kind) == FailureClass::Retryable`.
- A nonretryable `CoreError` therefore cannot become retryable merely because the broad `ErrorKind` is normally transient.
- A retryable flag also cannot promote a permanent/nonretryable error kind into the retry loop.

Deterministic unit coverage in `core/src/retry.rs` verifies:

- retryable network timeout succeeds after retry (`retries_retryable_transient_network_failure_until_success`),
- retryable HTTP status succeeds after retry (`retries_retryable_transient_http_failure`),
- nonretryable HTTP status is not retried (`nonretryable_http_status_is_not_retried`),
- nonretryable source-change failure is not retried (`nonretryable_source_change_is_not_retried`),
- permanent failures are not retried (`permanent_failure_is_not_retried`),
- a retryable flag does not promote a permanent error kind (`retryable_flag_does_not_promote_permanent_error_kind`), and
- retry attempts stop at the configured bound (`stops_after_bounded_attempt_count`).

Production-worker regression coverage in `core/src/rmd_401_retry_authority_tests.rs` verifies:

- `worker_treats_http_404_as_terminal_nonretryable_failure`,
- `worker_treats_http_503_as_retryable_transient_failure`, and
- `nonretryable_source_changed_error_is_never_promoted_by_kind`.

Those tests exercise the durable worker path rather than only the standalone retry helper.

## RMD-402 — one durable retry policy

`core/src/retry.rs::plan_retry_after_failure` applies `DownloadPolicy.max_attempts` as the durable retry bound and persists scheduler-visible retry state:

- the next durable attempt count is stored on the snapshot,
- the typed last error is stored on the snapshot,
- retryable failures move to `DownloadState::RetryWait` only while another attempt remains,
- terminal failures clear `retry_at_epoch_ms`, and
- retry deadlines are computed with injectable `RetryTiming` so tests can use deterministic time and jitter.

The retry delay remains bounded by `core/src/download.rs::retry_delay`, while `FixedRetryTiming` makes retry-deadline calculation deterministic for tests.

Unit coverage in `core/src/retry.rs` verifies persisted attempts and deadlines (`retry_policy_persists_attempt_and_next_eligible_deadline`), max-attempt terminal behavior (`max_attempts_one_fails_after_initial_attempt`), deadline clearing for nonretryable failure (`nonretryable_failure_clears_retry_deadline`), retry-wait survival across startup reconciliation (`persisted_retry_wait_survives_startup_reconciliation`), and cancellation before a first attempt (`cancellation_prevents_first_attempt`).

Production-worker and reconciliation coverage in `core/src/rmd_402_retry_policy_tests.rs` verifies:

- `worker_max_attempts_one_makes_retryable_failure_terminal`,
- `retry_wait_is_not_reclaimed_until_next_eligible_deadline`,
- `retry_wait_survives_startup_reconciliation_without_attempt_drift`, and
- `retry_wait_becomes_eligible_at_its_persisted_deadline`.

Those tests prove process-death/startup reconciliation preserves retry semantics instead of drifting attempts or prematurely reclaiming retry-wait work.

## Reconciliation boundary

RMD-401 and RMD-402 appear implemented and covered by deterministic Rust tests plus production-worker regression tests on current master. This note intentionally does not claim RMD-403 through RMD-408, and it does not update canonical checkboxes. A later canonical TODO reconciliation should cite this evidence only after the exact branch head containing this note passes the required CI gates and is merged to `master`.
