use crate::{CoreError, DownloadState, LibraryStore};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

/// Poll cadence for translating durable control state into the transfer engine's cooperative
/// cancellation flag. The engine checks that flag before every 64 KiB response-body read.
pub const PAUSE_POLL_INTERVAL: Duration = Duration::from_millis(25);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DurableStopReason {
    Pause,
    Cancel,
}

/// Returns the durable stop reason for an active job. This deliberately reads the queue source of
/// truth rather than keeping a second service-local pause boolean.
pub fn durable_stop_reason(
    library: &LibraryStore,
    job_id: &str,
) -> Result<Option<DurableStopReason>, CoreError> {
    let reason = library
        .load_download_snapshots()?
        .into_iter()
        .find(|snapshot| snapshot.job_id == job_id)
        .and_then(|snapshot| match snapshot.state {
            DownloadState::Paused => Some(DurableStopReason::Pause),
            DownloadState::Canceled => Some(DurableStopReason::Cancel),
            _ => None,
        });
    Ok(reason)
}

/// Bridge durable pause/cancel state to an in-flight transfer's cooperative stop flag.
///
/// The caller owns the thread/lifecycle around this bounded polling step. Returning `true` means
/// the transfer flag was raised because durable state requested a stop.
pub fn propagate_durable_stop(
    library: &LibraryStore,
    job_id: &str,
    transfer_stop: &AtomicBool,
) -> Result<Option<DurableStopReason>, CoreError> {
    let reason = durable_stop_reason(library, job_id)?;
    if reason.is_some() {
        transfer_stop.store(true, Ordering::Release);
    }
    Ok(reason)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{DurableDownloadSnapshot, LibraryStore};

    fn save(store: &LibraryStore, state: DownloadState) {
        store
            .save_download_snapshot(&DurableDownloadSnapshot {
                job_id: "job".into(),
                state,
                bytes_downloaded: 64,
                total_bytes: Some(1024),
                attempt: 1,
                retry_at_epoch_ms: None,
                last_error: None,
            })
            .unwrap();
    }

    #[test]
    fn paused_state_raises_cooperative_transfer_stop() {
        let store = LibraryStore::open_in_memory().unwrap();
        save(&store, DownloadState::Paused);
        let stop = AtomicBool::new(false);

        assert_eq!(
            propagate_durable_stop(&store, "job", &stop).unwrap(),
            Some(DurableStopReason::Pause)
        );
        assert!(stop.load(Ordering::Acquire));
    }

    #[test]
    fn canceled_state_raises_same_bounded_transfer_stop() {
        let store = LibraryStore::open_in_memory().unwrap();
        save(&store, DownloadState::Canceled);
        let stop = AtomicBool::new(false);

        assert_eq!(
            propagate_durable_stop(&store, "job", &stop).unwrap(),
            Some(DurableStopReason::Cancel)
        );
        assert!(stop.load(Ordering::Acquire));
    }

    #[test]
    fn active_state_does_not_raise_stop() {
        let store = LibraryStore::open_in_memory().unwrap();
        save(&store, DownloadState::Downloading);
        let stop = AtomicBool::new(false);

        assert_eq!(propagate_durable_stop(&store, "job", &stop).unwrap(), None);
        assert!(!stop.load(Ordering::Acquire));
    }
}
