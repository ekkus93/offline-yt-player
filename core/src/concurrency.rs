use crate::domain::{CoreError, ErrorKind};
use std::sync::{Condvar, Mutex};

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
        if limit == 0 {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Concurrent download limit must be at least one",
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
    fn zero_limit_is_rejected() {
        let error = DownloadConcurrencyGate::new(0).unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
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
