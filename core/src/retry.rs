use crate::domain::{CoreError, ErrorKind};
use crate::download::{FailureClass, classify_error, retry_delay};
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

/// Execute a fallible operation with the download engine's bounded retry policy.
///
/// `max_attempts` includes the initial attempt. Cancellation is checked before
/// every attempt and during backoff in short slices so callers do not have to
/// wait for the full delay before a cancel becomes effective.
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
                if classify_error(&error.kind) == FailureClass::Permanent || attempt == attempts {
                    return Err(error);
                }
                interruptible_sleep(retry_delay(attempt, jitter_ms), cancel)?;
            }
        }
    }
    unreachable!("at least one retry attempt is always executed")
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

fn canceled() -> CoreError {
    CoreError::new(ErrorKind::Canceled, "Download canceled", false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicU32;

    #[test]
    fn retries_retryable_failure_until_success() {
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

    #[test]
    fn cancellation_prevents_first_attempt() {
        let cancel = AtomicBool::new(true);
        let error = execute_with_retry(4, 0, &cancel, |_| Ok::<_, CoreError>(())).unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
    }
}
