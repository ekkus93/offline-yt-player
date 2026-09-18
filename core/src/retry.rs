use crate::domain::{CoreError, ErrorKind};
use crate::download::{DownloadPolicy, FailureClass, classify_error, retry_delay};
use crate::events::DurableDownloadSnapshot;
use crate::state::DownloadState;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

/// Execute a fallible operation with the download engine's bounded retry policy.
///
/// `max_attempts` includes the initial attempt. Cancellation is checked before
/// every attempt and during backoff in short slices so callers do not have to
/// wait for the full delay before a cancel becomes effective.
///
/// The error's explicit `retryable` flag is authoritative: an error marked
/// nonretryable is never promoted to retryable merely because its broad
/// `ErrorKind` is normally transient.
pub fn execute_with_retry<T, F>(
    max_attempts: u32,
    jitter_ms: u64,
    cancel: &AtomicBool,
    mut operation: F,
) -> Result<T, CoreError>
where
    F: FnMut(u32) -> Result<T, CoreError>,
{
    let attempts = max_attempts.max(1);
    for attempt in 1..=attempts {
        if cancel.load(Ordering::Relaxed) {
            return Err(canceled());
        }
        match operation(attempt) {
            Ok(value) => return Ok(value),
            Err(error) => {
                if !is_retryable(&error) || attempt == attempts {
                    return Err(error);
                }
                interruptible_sleep(retry_delay(attempt, jitter_ms), cancel)?;
            }
        }
    }
    unreachable!("at least one retry attempt is always executed")
}

/// Injectable retry timing used when turning a failed durable job snapshot into its next
/// scheduler-visible state. Production code can provide wall-clock time and bounded jitter, while
/// tests can keep retry deadlines deterministic.
pub trait RetryTiming {
    fn now_epoch_ms(&self) -> u64;
    fn jitter_ms(&self, next_attempt: u32) -> u64;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct FixedRetryTiming {
    pub now_epoch_ms: u64,
    pub jitter_ms: u64,
}

impl RetryTiming for FixedRetryTiming {
    fn now_epoch_ms(&self) -> u64 {
        self.now_epoch_ms
    }

    fn jitter_ms(&self, _next_attempt: u32) -> u64 {
        self.jitter_ms
    }
}

/// Apply the authoritative download retry policy to a durable job snapshot after one failed
/// attempt. The returned snapshot is ready to persist before process exit: it carries the updated
/// attempt count, typed error, next retry deadline, and scheduler-visible state.
///
/// `DownloadPolicy.max_attempts` is the single bound here and includes the initial attempt.
/// Retryable errors move to `RetryWait` only while another attempt remains; all other failures
/// become terminal `Failed`.
#[must_use]
pub fn plan_retry_after_failure(
    mut snapshot: DurableDownloadSnapshot,
    error: CoreError,
    policy: &DownloadPolicy,
    timing: &impl RetryTiming,
) -> DurableDownloadSnapshot {
    let next_attempt = snapshot.attempt.saturating_add(1);
    let max_attempts = policy.max_attempts.max(1);
    let retryable = is_retryable(&error) && next_attempt < max_attempts;

    snapshot.attempt = next_attempt;
    snapshot.last_error = Some(error);
    if retryable {
        snapshot.state = DownloadState::RetryWait;
        snapshot.retry_at_epoch_ms = Some(timing.now_epoch_ms().saturating_add(
            duration_millis_u64(retry_delay(next_attempt, timing.jitter_ms(next_attempt))),
        ));
    } else {
        snapshot.state = DownloadState::Failed;
        snapshot.retry_at_epoch_ms = None;
    }
    snapshot
}

fn is_retryable(error: &CoreError) -> bool {
    error.retryable && classify_error(&error.kind) == FailureClass::Retryable
}

fn interruptible_sleep(
    duration: std::time::Duration,
    cancel: &AtomicBool,
) -> Result<(), CoreError> {
    let slice = std::time::Duration::from_millis(50);
    let mut remaining = duration;
    while !remaining.is_zero() {
        if cancel.load(Ordering::Relaxed) {
            return Err(canceled());
        }
        let sleep_for = remaining.min(slice);
        thread::sleep(sleep_for);
        remaining = remaining.saturating_sub(sleep_for);
    }
    Ok(())
}

fn duration_millis_u64(duration: std::time::Duration) -> u64 {
    u64::try_from(duration.as_millis()).unwrap_or(u64::MAX)
}

fn canceled() -> CoreError {
    CoreError::new(ErrorKind::Canceled, "Download canceled", false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicU32;

    #[test]
    fn retries_retryable_transient_network_failure_until_success() {
        let calls = AtomicU32::new(0);
        let result = execute_with_retry(3, 0, &AtomicBool::new(false), |_| {
            let call = calls.fetch_add(1, Ordering::Relaxed) + 1;
            if call < 2 {
                Err(CoreError::new(ErrorKind::NetworkTimeout, "timeout", true))
            } else {
                Ok("done")
            }
        })
        .unwrap();
        assert_eq!(result, "done");
        assert_eq!(calls.load(Ordering::Relaxed), 2);
    }

    #[test]
    fn retries_retryable_transient_http_failure() {
        let calls = AtomicU32::new(0);
        let result = execute_with_retry(3, 0, &AtomicBool::new(false), |_| {
            let call = calls.fetch_add(1, Ordering::Relaxed) + 1;
            if call < 2 {
                Err(CoreError::new(ErrorKind::HttpStatus, "HTTP 503", true))
            } else {
                Ok("done")
            }
        })
        .unwrap();
        assert_eq!(result, "done");
        assert_eq!(calls.load(Ordering::Relaxed), 2);
    }

    #[test]
    fn nonretryable_http_status_is_not_retried() {
        let calls = AtomicU32::new(0);
        let error = execute_with_retry(4, 0, &AtomicBool::new(false), |_| {
            calls.fetch_add(1, Ordering::Relaxed);
            Err::<(), _>(CoreError::new(ErrorKind::HttpStatus, "HTTP 404", false))
        })
        .unwrap_err();
        assert_eq!(error.kind, ErrorKind::HttpStatus);
        assert!(!error.retryable);
        assert_eq!(calls.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn nonretryable_source_change_is_not_retried() {
        let calls = AtomicU32::new(0);
        let error = execute_with_retry(4, 0, &AtomicBool::new(false), |_| {
            calls.fetch_add(1, Ordering::Relaxed);
            Err::<(), _>(CoreError::new(
                ErrorKind::SourceChanged,
                "provider response changed",
                false,
            ))
        })
        .unwrap_err();
        assert_eq!(error.kind, ErrorKind::SourceChanged);
        assert!(!error.retryable);
        assert_eq!(calls.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn permanent_failure_is_not_retried() {
        let calls = AtomicU32::new(0);
        let error = execute_with_retry(4, 0, &AtomicBool::new(false), |_| {
            calls.fetch_add(1, Ordering::Relaxed);
            Err::<(), _>(CoreError::new(ErrorKind::InvalidInput, "bad input", false))
        })
        .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(calls.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn retryable_flag_does_not_promote_permanent_error_kind() {
        let calls = AtomicU32::new(0);
        let error = execute_with_retry(4, 0, &AtomicBool::new(false), |_| {
            calls.fetch_add(1, Ordering::Relaxed);
            Err::<(), _>(CoreError::new(ErrorKind::InvalidInput, "bad input", true))
        })
        .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(calls.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn stops_after_bounded_attempt_count() {
        let calls = AtomicU32::new(0);
        let error = execute_with_retry(2, 0, &AtomicBool::new(false), |_| {
            calls.fetch_add(1, Ordering::Relaxed);
            Err::<(), _>(CoreError::new(ErrorKind::NetworkTimeout, "timeout", true))
        })
        .unwrap_err();
        assert_eq!(error.kind, ErrorKind::NetworkTimeout);
        assert_eq!(calls.load(Ordering::Relaxed), 2);
    }

    fn durable_snapshot(state: DownloadState, attempt: u32) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: "job-1".into(),
            state,
            bytes_downloaded: 512,
            total_bytes: Some(1024),
            attempt,
            retry_at_epoch_ms: None,
            last_error: None,
        }
    }

    #[test]
    fn retry_policy_persists_attempt_and_next_eligible_deadline() {
        let policy = DownloadPolicy {
            max_attempts: 3,
            ..DownloadPolicy::default()
        };
        let timing = FixedRetryTiming {
            now_epoch_ms: 10_000,
            jitter_ms: 250,
        };
        let planned = plan_retry_after_failure(
            durable_snapshot(DownloadState::Downloading, 0),
            CoreError::new(ErrorKind::NetworkTimeout, "timeout", true),
            &policy,
            &timing,
        );

        assert_eq!(planned.state, DownloadState::RetryWait);
        assert_eq!(planned.attempt, 1);
        assert_eq!(planned.retry_at_epoch_ms, Some(11_250));
        assert_eq!(planned.bytes_downloaded, 512);
        assert_eq!(planned.total_bytes, Some(1024));
        assert_eq!(planned.last_error.unwrap().kind, ErrorKind::NetworkTimeout);
    }

    #[test]
    fn max_attempts_one_fails_after_initial_attempt() {
        let policy = DownloadPolicy {
            max_attempts: 1,
            ..DownloadPolicy::default()
        };
        let planned = plan_retry_after_failure(
            durable_snapshot(DownloadState::Downloading, 0),
            CoreError::new(ErrorKind::NetworkTimeout, "timeout", true),
            &policy,
            &FixedRetryTiming {
                now_epoch_ms: 10_000,
                jitter_ms: 0,
            },
        );

        assert_eq!(planned.state, DownloadState::Failed);
        assert_eq!(planned.attempt, 1);
        assert_eq!(planned.retry_at_epoch_ms, None);
    }

    #[test]
    fn nonretryable_failure_clears_retry_deadline() {
        let mut snapshot = durable_snapshot(DownloadState::RetryWait, 1);
        snapshot.retry_at_epoch_ms = Some(99_999);
        let planned = plan_retry_after_failure(
            snapshot,
            CoreError::new(ErrorKind::HttpStatus, "HTTP 404", false),
            &DownloadPolicy::default(),
            &FixedRetryTiming {
                now_epoch_ms: 10_000,
                jitter_ms: 0,
            },
        );

        assert_eq!(planned.state, DownloadState::Failed);
        assert_eq!(planned.attempt, 2);
        assert_eq!(planned.retry_at_epoch_ms, None);
    }

    #[test]
    fn persisted_retry_wait_survives_startup_reconciliation() {
        let store = crate::LibraryStore::open_in_memory().unwrap();
        let planned = plan_retry_after_failure(
            durable_snapshot(DownloadState::Downloading, 0),
            CoreError::new(ErrorKind::NetworkUnavailable, "offline", true),
            &DownloadPolicy::default(),
            &FixedRetryTiming {
                now_epoch_ms: 50_000,
                jitter_ms: 0,
            },
        );
        store.save_download_snapshot(&planned).unwrap();

        assert_eq!(
            crate::reconcile_startup_downloads(&store)
                .unwrap()
                .jobs_requeued,
            0
        );
        assert_eq!(store.load_download_snapshots().unwrap(), vec![planned]);
    }

    #[test]
    fn cancellation_prevents_first_attempt() {
        let cancel = AtomicBool::new(true);
        let error = execute_with_retry(4, 0, &cancel, |_| Ok::<_, CoreError>(())).unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
    }
}
