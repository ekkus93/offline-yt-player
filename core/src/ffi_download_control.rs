use crate::{CoreError, DownloadState, DownloadStateMachine, ErrorKind, LibraryStore};
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

    pub fn pause(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Paused)
    }

    pub fn resume(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Downloading)
    }

    pub fn cancel(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Canceled)
    }
}

impl FfiDownloadControlService {
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
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{DurableDownloadSnapshot, FfiErrorKind};

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
        assert!(service.cancel("job-1".into()).updated);
        assert_eq!(
            store.load_download_snapshots().unwrap()[0].state,
            DownloadState::Canceled
        );
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
