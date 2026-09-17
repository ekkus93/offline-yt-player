use crate::{CoreError, DownloadState, DurableDownloadSnapshot, ErrorKind, LibraryStore};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct StartupReconciliation {
    pub jobs_requeued: usize,
}

/// Reconcile durable queue state after an unclean process exit.
///
/// States that require an actively running worker cannot truthfully survive process death. They
/// are moved back to `Queued` so the Android service can explicitly reconstruct and restart them.
/// User-paused jobs remain paused, retry waits retain their durable deadline, and terminal states
/// are left untouched.
pub fn reconcile_startup_downloads(
    store: &LibraryStore,
) -> Result<StartupReconciliation, CoreError> {
    let snapshots = store.load_download_snapshots()?;
    let mut jobs_requeued = 0;
    for mut snapshot in snapshots {
        if requires_live_worker(snapshot.state) {
            snapshot.state = DownloadState::Queued;
            snapshot.retry_at_epoch_ms = None;
            snapshot.last_error = Some(CoreError::new(
                ErrorKind::Internal,
                "download was interrupted by process shutdown and has been queued for recovery",
                true,
            ));
            store.save_download_snapshot(&snapshot)?;
            jobs_requeued += 1;
        }
    }
    Ok(StartupReconciliation { jobs_requeued })
}

const fn requires_live_worker(state: DownloadState) -> bool {
    matches!(
        state,
        DownloadState::Resolving | DownloadState::Downloading | DownloadState::Verifying
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: job_id.into(),
            state,
            bytes_downloaded: 128,
            total_bytes: Some(1024),
            attempt: 2,
            retry_at_epoch_ms: Some(500),
            last_error: None,
        }
    }

    #[test]
    fn requeues_only_states_that_require_a_live_worker() {
        let store = LibraryStore::open_in_memory().unwrap();
        let states = [
            DownloadState::Queued,
            DownloadState::Resolving,
            DownloadState::Downloading,
            DownloadState::Paused,
            DownloadState::RetryWait,
            DownloadState::Failed,
            DownloadState::Verifying,
            DownloadState::Completed,
            DownloadState::Canceled,
        ];
        for (index, state) in states.into_iter().enumerate() {
            store
                .save_download_snapshot(&snapshot(&format!("job-{index}"), state))
                .unwrap();
        }

        let result = reconcile_startup_downloads(&store).unwrap();
        assert_eq!(result.jobs_requeued, 3);
        let reconciled = store.load_download_snapshots().unwrap();
        for (index, before) in states.into_iter().enumerate() {
            let after = reconciled
                .iter()
                .find(|value| value.job_id == format!("job-{index}"))
                .unwrap();
            if requires_live_worker(before) {
                assert_eq!(after.state, DownloadState::Queued);
                assert!(after.retry_at_epoch_ms.is_none());
                assert!(
                    after
                        .last_error
                        .as_ref()
                        .is_some_and(|error| error.retryable)
                );
            } else {
                assert_eq!(after.state, before);
                assert_eq!(after.retry_at_epoch_ms, Some(500));
                assert!(after.last_error.is_none());
            }
        }
    }

    #[test]
    fn reconciliation_is_idempotent() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("active", DownloadState::Downloading))
            .unwrap();
        assert_eq!(
            reconcile_startup_downloads(&store).unwrap().jobs_requeued,
            1
        );
        assert_eq!(
            reconcile_startup_downloads(&store).unwrap().jobs_requeued,
            0
        );
    }
}
