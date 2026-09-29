use crate::{
    build_download_work_if_supported_source_url, CoreError, DownloadState, DownloadStateMachine,
    DurableDownloadSnapshot, DurableDownloadWorkStore, ErrorKind, LibraryStore,
};
use std::path::PathBuf;
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
    database_path: PathBuf,
    library: LibraryStore,
}

#[uniffi::export]
impl FfiDownloadControlService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiDownloadControlOpenError> {
        let database_path = PathBuf::from(database_path);
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { database_path, library }))
            .map_err(|error| FfiDownloadControlOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn enqueue(&self, job_id: String) -> FfiDownloadControlResult {
        match self.enqueue_inner(&job_id) {
            Ok(updated) => FfiDownloadControlResult { updated, error: None },
            Err(error) => FfiDownloadControlResult {
                updated: false,
                error: Some(crate::ffi::FfiError::from(&error)),
            },
        }
    }

    pub fn pause(&self, job_id: String) -> FfiDownloadControlResult { self.transition(&job_id, DownloadState::Paused) }
    pub fn resume(&self, job_id: String) -> FfiDownloadControlResult { self.transition(&job_id, DownloadState::Queued) }
    pub fn cancel(&self, job_id: String) -> FfiDownloadControlResult { self.transition(&job_id, DownloadState::Canceled) }

    pub fn retry(&self, job_id: String) -> FfiDownloadControlResult {
        match self.retry_inner(&job_id) {
            Ok(updated) => FfiDownloadControlResult { updated, error: None },
            Err(error) => FfiDownloadControlResult {
                updated: false,
                error: Some(crate::ffi::FfiError::from(&error)),
            },
        }
    }
}

impl FfiDownloadControlService {
    fn enqueue_inner(&self, job_id: &str) -> Result<bool, CoreError> {
        if job_id.trim().is_empty() { return Err(CoreError::new(ErrorKind::InvalidInput, "download job id must not be empty", false)); }
        let snapshots = self.library.load_download_snapshots()?;
        if snapshots.iter().any(|snapshot| snapshot.job_id == job_id) { return Ok(false); }
        let executable_work = build_download_work_if_supported_source_url(job_id)?;
        let work_store = match &executable_work { Some(_) => Some(DurableDownloadWorkStore::open(&self.database_path)?), None => None };
        if let (Some(store), Some(work)) = (&work_store, &executable_work) { store.save(work)?; }
        let save_result = self.library.save_download_snapshot(&DurableDownloadSnapshot {
            job_id: job_id.into(), state: DownloadState::Queued, bytes_downloaded: 0,
            total_bytes: executable_work.as_ref().and_then(|work| work.plan.quality.estimated_bytes),
            attempt: 0, retry_at_epoch_ms: None, last_error: None,
        });
        if let Err(error) = save_result {
            if let Some(store) = work_store { let _ = store.delete(job_id); }
            return Err(error);
        }
        Ok(true)
    }

    fn transition(&self, job_id: &str, next: DownloadState) -> FfiDownloadControlResult {
        match self.transition_inner(job_id, next) {
            Ok(updated) => FfiDownloadControlResult { updated, error: None },
            Err(error) => FfiDownloadControlResult { updated: false, error: Some(crate::ffi::FfiError::from(&error)) },
        }
    }

    fn transition_inner(&self, job_id: &str, next: DownloadState) -> Result<bool, CoreError> {
        let mut snapshots = self.library.load_download_snapshots()?;
        let snapshot = snapshots.iter_mut().find(|snapshot| snapshot.job_id == job_id).ok_or_else(|| CoreError::new(ErrorKind::InvalidInput, "download job does not exist", false))?;
        if snapshot.state == next { return Ok(false); }
        let mut machine = DownloadStateMachine::new(snapshot.state);
        machine.transition(next)?;
        snapshot.state = machine.state();
        self.library.save_download_snapshot(snapshot)?;
        Ok(true)
    }

    fn retry_inner(&self, job_id: &str) -> Result<bool, CoreError> {
        let mut snapshots = self.library.load_download_snapshots()?;
        let snapshot = snapshots.iter_mut().find(|snapshot| snapshot.job_id == job_id).ok_or_else(|| CoreError::new(ErrorKind::InvalidInput, "download job does not exist", false))?;
        if snapshot.state != DownloadState::Failed { return Err(CoreError::new(ErrorKind::InvalidInput, "download is not eligible for explicit retry", false)); }
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
