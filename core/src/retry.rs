use crate::{CoreError, FailureClass, classify_error, retry_delay};
use std::time::Duration;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RetryWait {
    pub failed_attempt: u32,
    pub next_attempt: u32,
    pub delay: Duration,
    pub error: CoreError,
}

/// Executes a fallible operation with bounded retries.
///
/// `on_retry` is invoked before every retry so callers can persist or publish a
/// retry-wait state. `sleep` is injected so deterministic tests do not wait in
/// real time; production callers normally pass `std::thread::sleep`.
pub fn retry_operation<T>(
    max_attempts: u32,
    jitter_ms: u64,
    mut operation: impl FnMut(u32) -> Result<T, CoreError>,
    mut on_retry: impl FnMut(&RetryWait),
    mut sleep: impl FnMut(Duration),
) -> Result<T, CoreError> {
    let max_attempts = max_attempts.max(1);
    for attempt in 1..=max_attempts {
        match operation(attempt) {
            Ok(value) => return Ok(value),
            Err(error) => {
                let exhausted = attempt == max_attempts;
                if exhausted || classify_error(&error.kind) == FailureClass::Permanent {
                    return Err(error);
                }
                let delay = retry_delay(attempt, jitter_ms);
                on_retry(&RetryWait {
                    failed_attempt: attempt,
                    next_attempt: attempt + 1,
                    delay,
                    error,
                });
                sleep(delay);
            }
        }
    }
    unreachable!("max_attempts is normalized to at least one")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ErrorKind;

    #[test]
    fn retryable_failure_retries_and_reports_wait() {
        let mut attempts = 0;
        let mut waits = Vec::new();
        let mut sleeps = Vec::new();
        let value = retry_operation(
            3,
            25,
            |_| {
                attempts += 1;
                if attempts < 3 {
                    Err(CoreError::new(ErrorKind::NetworkTimeout, "timeout", true))
                } else {
                    Ok("done")
                }
            },
            |wait| waits.push(wait.clone()),
            |delay| sleeps.push(delay),
        )
        .unwrap();
        assert_eq!(value, "done");
        assert_eq!(attempts, 3);
        assert_eq!(waits.len(), 2);
        assert_eq!(waits[0].next_attempt, 2);
        assert_eq!(sleeps, vec![Duration::from_millis(1_025), Duration::from_millis(2_025)]);
    }

    #[test]
    fn permanent_failure_never_retries() {
        let mut attempts = 0;
        let error = retry_operation::<()>(
            4,
            0,
            |_| {
                attempts += 1;
                Err(CoreError::new(ErrorKind::InvalidInput, "bad input", false))
            },
            |_| panic!("permanent failures must not enter retry wait"),
            |_| panic!("permanent failures must not sleep"),
        )
        .unwrap_err();
        assert_eq!(attempts, 1);
        assert_eq!(error.kind, ErrorKind::InvalidInput);
    }

    #[test]
    fn retry_budget_is_strictly_bounded() {
        let mut attempts = 0;
        let error = retry_operation::<()>(
            2,
            0,
            |_| {
                attempts += 1;
                Err(CoreError::new(ErrorKind::NetworkUnavailable, "offline", true))
            },
            |_| {},
            |_| {},
        )
        .unwrap_err();
        assert_eq!(attempts, 2);
        assert_eq!(error.kind, ErrorKind::NetworkUnavailable);
    }
}
