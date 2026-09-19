use crate::{
    CoreError, DownloadState, DownloadStateMachine, DurableDownloadSnapshot, ErrorKind,
    LibraryStore,
};
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiDownloadControlOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadControlResult {
    pub updated: bool,
    pub error: Option<crate::ffi::FfiError>,
}

#[derive(Debug, uniffi::Object)]
pub struct FfiDownloadControlService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiDownloadControlService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiDownloadControlOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiDownloadControlOpenError::Persistence {
                message: error.message,
            })
    }

    /// Persist a new durable queued job. The caller supplies the stable job identifier used by the
    /// Android service and subsequent pause/resume/cancel operations.
    pub fn enqueue(&self, job_id: String) -> FfiDownloadControlResult {
        match self.enqueue_inner(&job_id) {
            Ok(updated) => FfiDownloadControlResult {
                updated,
                error: None,
            },
            Err(error) => FfiDownloadControlResult {
                updated: false,
                error: Some(crate::ffi::FfiError::from(&error)),
            },
        }
    }

    pub fn pause(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Paused)
    }

    /// Resume makes paused work eligible for the worker claim loop again. The worker owns the
    /// Resolving -> Downloading transition, and DownloadEngine revalidates retained partials.
    pub fn resume(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Queued)
    }

    pub fn cancel(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Canceled)
    }

    /// Explicit user Retry is distinct from automatic retry. It is legal only from terminal
    /// Failed state and starts a fresh bounded attempt budget for the same durable job identity.
    /// Partial progress/size metadata is preserved for normal continuation revalidation, while
    /// stale retry timing and the prior terminal error are cleared.
    pub fn retry(&self, job_id: String) -> FfiDownloadControlResult {
        match self.retry_inner(&job_id) {
            Ok(updated) => FfiDownloadControlResult {
                updated,
                error: None,
            },
            Err(error) => FfiDownloadControlResult {
                updated: false,
                error: Some(crate::ffi::FfiError::from(&error)),
            },
        }
    }
}

impl FfiDownloadControlService {
    fn enqueue_inner(&self, job_id: &str) -> Result<bool, CoreError> {
        if job_id.trim().is_empty() {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "download job id must not be empty",
                false,
            ));
        }
        let snapshots = self.library.load_download_snapshots()?;
        if snapshots.iter().any(|snapshot| snapshot.job_id == job_id) {
            return Ok(false);
        }
        self.library
            .save_download_snapshot(&DurableDownloadSnapshot {
                job_id: job_id.into(),
                state: DownloadState::Queued,
                bytes_downloaded: 0,
                total_bytes: None,
                attempt: 0,
                retry_at_epoch_ms: None,
                last_error: None,
            })?;
        Ok(true)
    }

    fn transition(&self, job_id: &str, next: DownloadState) -> FfiDownloadControlResult {
        match self.transition_inner(job_id, next) {
            Ok(updated) => FfiDownloadControlResult {
                updated,
                error: None,
            },
            Err(error) => FfiDownloadControlResult {
                updated: false,
                error: Some(crate::ffi::FfiError::from(&error)),
            },
        }
    }

    fn transition_inner(&self, job_id: &str, next: DownloadState) -> Result<bool, CoreError> {
        let mut snapshots = self.library.load_download_snapshots()?;
        let snapshot = snapshots
            .iter_mut()
            .find(|snapshot| snapshot.job_id == job_id)
            .ok_or_else(|| {
                CoreError::new(
                    ErrorKind::InvalidInput,
                    "download job does not exist",
                    false,
                )
            })?;

        if snapshot.state == next {
            return Ok(false);
        }

        let mut machine = DownloadStateMachine::new(snapshot.state);
        machine.transition(next)?;
        snapshot.state = machine.state();
        self.library.save_download_snapshot(snapshot)?;
        Ok(true)
    }

    fn retry_inner(&self, job_id: &str) -> Result<bool, CoreError> {
        let mut snapshots = self.library.load_download_snapshots()?;
        let snapshot = snapshots
            .iter_mut()
            .find(|snapshot| snapshot.job_id == job_id)
            .ok_or_else(|| {
                CoreError::new(
                    ErrorKind::InvalidInput,
                    "download job does not exist",
                    false,
                )
            })?;

        if snapshot.state != DownloadState::Failed {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "download is not eligible for explicit retry",
                false,
            ));
        }

        let mut machine = DownloadStateMachine::new(snapshot.state);
        machine.transition(DownloadState::Queued)?;
        snapshot.state = machine.state();
        snapshot.attempt = 0;
        snapshot.retry_at_epoch_ms = None;
        snapshot.last_error = None;
        self.library.save_download_snapshot(snapshot)?;
        Ok(true)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::FfiErrorKind;

    fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: job_id.into(),
            state,
            bytes_downloaded: 128,
            total_bytes: Some(1024),
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: None,
        }
    }

    #[test]
    fn enqueue_persists_a_new_queued_job_and_is_idempotent() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        let first = service.enqueue("job-new".into());
        assert!(first.updated);
        assert!(first.error.is_none());
        let saved = store.load_download_snapshots().unwrap();
        assert_eq!(saved.len(), 1);
        assert_eq!(saved[0].job_id, "job-new");
        assert_eq!(saved[0].state, DownloadState::Queued);
        assert_eq!(saved[0].bytes_downloaded, 0);

        let repeated = service.enqueue("job-new".into());
        assert!(!repeated.updated);
        assert!(repeated.error.is_none());
        assert_eq!(store.load_download_snapshots().unwrap().len(), 1);
    }

    #[test]
    fn enqueue_rejects_empty_job_id_with_typed_error() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        let result = service.enqueue("  ".into());
        assert!(!result.updated);
        assert_eq!(result.error.unwrap().kind, FfiErrorKind::InvalidInput);
    }

    #[test]
    fn pause_resume_and_cancel_persist_legal_transitions() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("job-1", DownloadState::Downloading))
            .unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        assert!(service.pause("job-1".into()).updated);
        assert_eq!(
            store.load_download_snapshots().unwrap()[0].state,
            DownloadState::Paused
        );
        assert!(service.resume("job-1".into()).updated);
        assert_eq!(
            store.load_download_snapshots().unwrap()[0].state,
            DownloadState::Queued
        );
        assert!(service.cancel("job-1".into()).updated);
        assert_eq!(
            store.load_download_snapshots().unwrap()[0].state,
            DownloadState::Canceled
        );
    }

    #[test]
    fn resume_survives_reopen_and_preserves_partial_progress() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("paused", DownloadState::Paused))
            .unwrap();
        drop(store);

        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();
        assert!(service.resume("paused".into()).updated);
        drop(service);

        let reopened = LibraryStore::open(&database).unwrap();
        let durable = reopened.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Queued);
        assert_eq!(durable.bytes_downloaded, 128);
    }

    #[test]
    fn explicit_retry_is_failed_only_and_resets_only_attempt_error_fields() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        let mut failed = snapshot("failed", DownloadState::Failed);
        failed.attempt = 3;
        failed.retry_at_epoch_ms = Some(55_000);
        failed.last_error = Some(CoreError::new(ErrorKind::NetworkTimeout, "timeout", true));
        store.save_download_snapshot(&failed).unwrap();
        store
            .save_download_snapshot(&snapshot("active", DownloadState::Downloading))
            .unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        let retry = service.retry("failed".into());
        assert!(retry.updated);
        assert!(retry.error.is_none());
        let retried = store
            .load_download_snapshots()
            .unwrap()
            .into_iter()
            .find(|item| item.job_id == "failed")
            .unwrap();
        assert_eq!(retried.state, DownloadState::Queued);
        assert_eq!(retried.attempt, 0);
        assert_eq!(retried.retry_at_epoch_ms, None);
        assert_eq!(retried.last_error, None);
        assert_eq!(retried.bytes_downloaded, 128);
        assert_eq!(retried.total_bytes, Some(1024));

        let ineligible = service.retry("active".into());
        assert!(!ineligible.updated);
        assert_eq!(ineligible.error.unwrap().kind, FfiErrorKind::InvalidInput);
        assert_eq!(
            store
                .load_download_snapshots()
                .unwrap()
                .into_iter()
                .find(|item| item.job_id == "active")
                .unwrap()
                .state,
            DownloadState::Downloading
        );
    }

    #[test]
    fn explicit_retry_reuses_same_durable_identity_without_duplicate_snapshot() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("same-job", DownloadState::Failed))
            .unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        assert!(service.retry("same-job".into()).updated);
        let saved = store.load_download_snapshots().unwrap();
        assert_eq!(saved.len(), 1);
        assert_eq!(saved[0].job_id, "same-job");
        assert_eq!(saved[0].state, DownloadState::Queued);
    }

    #[test]
    fn invalid_transition_and_missing_job_are_typed_errors() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("done", DownloadState::Completed))
            .unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        let illegal = service.pause("done".into());
        assert_eq!(illegal.error.unwrap().kind, FfiErrorKind::Internal);

        let missing = service.cancel("missing".into());
        assert_eq!(missing.error.unwrap().kind, FfiErrorKind::InvalidInput);
    }

    #[test]
    fn repeated_target_state_is_an_idempotent_no_op() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("paused", DownloadState::Paused))
            .unwrap();
        let service =
            FfiDownloadControlService::open(database.to_string_lossy().into_owned()).unwrap();

        let result = service.pause("paused".into());
        assert!(!result.updated);
        assert!(result.error.is_none());
    }
}
