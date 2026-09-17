# OYP-503 retry-policy audit

This audit records the implemented generic download retry boundary.

## Retryable versus permanent failures

`classify_error` maps transient network unavailability, timeout, HTTP-status, and source-change failures to `FailureClass::Retryable`; invalid input, integrity, storage, persistence, cancellation, and other non-transient categories remain permanent. `execute_with_retry` stops immediately on permanent failures and after the configured attempt bound.

## Bounded exponential backoff and jitter

`retry_delay` uses exponential one-second-based growth with a capped exponent, clamps caller-provided jitter to 750 ms, and caps the resulting delay at 30 seconds. `execute_with_retry` enforces `max_attempts` (with a minimum of one attempt) and sleeps in short interruptible slices so cancellation does not wait for an entire backoff interval.

## Retry state visible to UI

`DownloadState` includes the durable/coarse `RetryWait` state. The state machine permits active resolving/downloading/verifying work to enter `RetryWait`, and permits `RetryWait` to return through resolving/downloading or terminate as failed/canceled. This keeps retry waiting representable through the same persisted/UI-facing download-state boundary rather than hiding retries inside an opaque transfer loop.

## Qualification

Unit tests prove retryable failure reaches a later successful attempt, permanent failure is not retried, configured attempt count is a hard bound, pre-cancellation prevents work, retry delay remains bounded, and the exhaustive state-machine matrix verifies every legal and illegal `RetryWait` transition.

These implementation and test boundaries satisfy all three explicit OYP-503 checklist requirements. Canonical TODO reconciliation should mark OYP-503 complete after this audit's exact-head CI passes and the change is merged.
