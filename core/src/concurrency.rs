use crate::domain::{CoreError, ErrorKind};
use std::sync::{Condvar, Mutex};

/// Hard portable-core ceiling for concurrent download transfers.
///
/// Platform preferences may request fewer workers, but no caller may construct a gate above this
/// value. Keeping the resource ceiling in the portable core prevents Android or another frontend
/// from accidentally exceeding the limit through a duplicated platform constant.
pub const MAX_CONCURRENT_DOWNLOADS: usize = 4;

/// Product default used when the user has not selected a concurrency preference.
pub const DEFAULT_CONCURRENT_DOWNLOADS: usize = 2;

/// Bound a platform/user preference to the range the portable core can safely enforce.
#[must_use]
pub const fn bounded_download_concurrency(requested: usize) -> usize {
    if requested == 0 {
        1
    } else if requested > MAX_CONCURRENT_DOWNLOADS {
        MAX_CONCURRENT_DOWNLOADS
    } else {
        requested
    }
}

/// Process-local admission gate for concurrent download work.
///
/// Durable queue ownership remains above this primitive; the gate only guarantees that no more
/// than `limit` workers can enter the transfer section at once. A permit is released on drop,
/// including early-return and panic-unwind paths.
#[derive(Debug)]
pub struct DownloadConcurrencyGate {
    limit: usize,
    active: Mutex<usize>,
    changed: Condvar,
}

impl DownloadConcurrencyGate {
    pub fn new(limit: usize) -> Result<Self, CoreError> {
        if limit == 0 || limit > MAX_CONCURRENT_DOWNLOADS {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                format!(
                    "Concurrent download limit must be between one and {MAX_CONCURRENT_DOWNLOADS}"
                ),
                false,
            ));
        }
        Ok(Self {
            limit,
            active: Mutex::new(0),
            changed: Condvar::new(),
        })
    }

    #[must_use]
    pub const fn limit(&self) -> usize {
        self.limit
    }

    pub fn acquire(&self) -> DownloadPermit<'_> {
        let mut active = self
            .active
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        while *active >= self.limit {
            active = self
                .changed
                .wait(active)
                .unwrap_or_else(|poisoned| poisoned.into_inner());
        }
        *active += 1;
        DownloadPermit { gate: self }
    }

    #[must_use]
    pub fn try_acquire(&self) -> Option<DownloadPermit<'_>> {
        let mut active = self
            .active
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if *active >= self.limit {
            return None;
        }
        *active += 1;
        Some(DownloadPermit { gate: self })
    }

    #[must_use]
    pub fn active(&self) -> usize {
        *self
            .active
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

#[derive(Debug)]
pub struct DownloadPermit<'a> {
    gate: &'a DownloadConcurrencyGate,
}

impl Drop for DownloadPermit<'_> {
    fn drop(&mut self) {
        let mut active = self
            .gate
            .active
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        *active = active.saturating_sub(1);
        self.gate.changed.notify_one();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Barrier};
    use std::thread;
    use std::time::Duration;

    #[test]
    fn invalid_limits_are_rejected() {
        for limit in [0, MAX_CONCURRENT_DOWNLOADS + 1, usize::MAX] {
            let error = DownloadConcurrencyGate::new(limit).unwrap_err();
            assert_eq!(error.kind, ErrorKind::InvalidInput);
        }
        assert_eq!(
            DownloadConcurrencyGate::new(MAX_CONCURRENT_DOWNLOADS)
                .unwrap()
                .limit(),
            MAX_CONCURRENT_DOWNLOADS
        );
    }

    #[test]
    fn platform_preferences_map_into_core_bound() {
        assert_eq!(bounded_download_concurrency(0), 1);
        assert_eq!(bounded_download_concurrency(1), 1);
        assert_eq!(
            bounded_download_concurrency(DEFAULT_CONCURRENT_DOWNLOADS),
            DEFAULT_CONCURRENT_DOWNLOADS
        );
        assert_eq!(
            bounded_download_concurrency(MAX_CONCURRENT_DOWNLOADS),
            MAX_CONCURRENT_DOWNLOADS
        );
        assert_eq!(
            bounded_download_concurrency(MAX_CONCURRENT_DOWNLOADS + 1),
            MAX_CONCURRENT_DOWNLOADS
        );
        assert_eq!(
            bounded_download_concurrency(usize::MAX),
            MAX_CONCURRENT_DOWNLOADS
        );
    }

    #[test]
    fn try_acquire_never_exceeds_limit_and_drop_releases_capacity() {
        let gate = DownloadConcurrencyGate::new(2).unwrap();
        let first = gate.try_acquire().unwrap();
        let second = gate.try_acquire().unwrap();
        assert_eq!(gate.active(), 2);
        assert!(gate.try_acquire().is_none());
        drop(first);
        let third = gate.try_acquire().unwrap();
        assert_eq!(gate.active(), 2);
        drop(second);
        drop(third);
        assert_eq!(gate.active(), 0);
    }

    #[test]
    fn blocking_acquire_waits_until_a_permit_is_released() {
        let gate = Arc::new(DownloadConcurrencyGate::new(1).unwrap());
        let held = gate.acquire();
        let started = Arc::new(Barrier::new(2));
        let worker_gate = Arc::clone(&gate);
        let worker_started = Arc::clone(&started);
        let worker = thread::spawn(move || {
            worker_started.wait();
            let _permit = worker_gate.acquire();
            worker_gate.active()
        });
        started.wait();
        thread::sleep(Duration::from_millis(25));
        assert_eq!(gate.active(), 1);
        drop(held);
        assert_eq!(worker.join().unwrap(), 1);
        assert_eq!(gate.active(), 0);
    }
}
