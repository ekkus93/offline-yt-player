# RMD-506 Retry Audit

RMD-506 distinguishes automatic retry from an explicit user Retry action.

- Automatic retry remains bounded by `DownloadPolicy.max_attempts`; exhausted or nonretryable work becomes terminal `Failed`.
- `FfiDownloadControlService.retry` accepts only `Failed` durable jobs. It never creates a second queue record or source/library identity; it updates the existing stable job snapshot.
- An explicit user Retry starts a fresh bounded automatic-attempt budget by resetting `attempt` to zero. This reset is reachable only through the explicit failed-state control action, so automatic retries cannot silently escape the configured maximum.
- Retry clears only stale attempt/error scheduling fields (`attempt`, `retry_at_epoch_ms`, `last_error`) and transitions `Failed -> Queued`. Existing transferred/total byte metadata is retained so the normal continuation validation path can decide whether a partial is reusable.
- Tests prove ineligible active work is rejected, a failed job becomes queued without duplication, and only the intended fields reset.
