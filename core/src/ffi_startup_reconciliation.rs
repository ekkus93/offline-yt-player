use crate::{
    FfiCoreServiceOpenError, FfiError, LibraryStore, reconcile_startup_downloads,
    reconcile_startup_with_library_root,
};
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiStartupReconciliationResult {
    pub jobs_requeued: u64,
    pub orphaned_staged_jobs_recovered: u64,
    pub asset_mismatches_reported: u64,
    pub orphaned_partial_files_removed: u64,
    pub error: Option<FfiError>,
}

#[derive(Debug, uniffi::Object)]
pub struct FfiStartupReconciliationService {
    library: LibraryStore,
    library_root: Option<PathBuf>,
}

#[uniffi::export]
impl FfiStartupReconciliationService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiCoreServiceOpenError> {
        let library_root = PathBuf::from(&database_path).parent().map(PathBuf::from);
        LibraryStore::open(&database_path)
            .map(|library| {
                Arc::new(Self {
                    library,
                    library_root,
                })
            })
            .map_err(|error| FfiCoreServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn startup_reconcile(&self) -> FfiStartupReconciliationResult {
        let result = if let Some(library_root) = &self.library_root {
            reconcile_startup_with_library_root(&self.library, library_root)
        } else {
            reconcile_startup_downloads(&self.library)
        };
        match result {
            Ok(result) => FfiStartupReconciliationResult {
                jobs_requeued: result.jobs_requeued as u64,
                orphaned_staged_jobs_recovered: result.orphaned_staged_jobs_recovered as u64,
                asset_mismatches_reported: result.asset_mismatches_reported as u64,
                orphaned_partial_files_removed: result.orphaned_partial_files_removed as u64,
                error: None,
            },
            Err(error) => FfiStartupReconciliationResult {
                jobs_requeued: 0,
                orphaned_staged_jobs_recovered: 0,
                asset_mismatches_reported: 0,
                orphaned_partial_files_removed: 0,
                error: Some(FfiError::from(&error)),
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{CoreError, DownloadState, DurableDownloadSnapshot, ErrorKind};

    fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: job_id.into(),
            state,
            bytes_downloaded: 10,
            total_bytes: Some(100),
            attempt: 1,
            retry_at_epoch_ms: Some(999),
            last_error: None,
        }
    }

    #[test]
    fn ffi_startup_reconciliation_requeues_interrupted_jobs() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("startup.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&snapshot("active", DownloadState::Downloading))
            .unwrap();
        store
            .save_download_snapshot(&snapshot("paused", DownloadState::Paused))
            .unwrap();
        drop(store);

        let service =
            FfiStartupReconciliationService::open(database.to_string_lossy().into_owned()).unwrap();
        let result = service.startup_reconcile();
        assert!(result.error.is_none());
        assert_eq!(result.jobs_requeued, 1);
        assert_eq!(result.orphaned_staged_jobs_recovered, 0);

        let store = LibraryStore::open(&database).unwrap();
        let active = store
            .load_download_snapshots()
            .unwrap()
            .into_iter()
            .find(|snapshot| snapshot.job_id == "active")
            .unwrap();
        assert_eq!(active.state, DownloadState::Queued);
        assert!(active.retry_at_epoch_ms.is_none());
        assert!(
            active
                .last_error
                .as_ref()
                .is_some_and(|error| error.retryable)
        );
    }

    #[test]
    fn ffi_startup_reconciliation_removes_orphaned_partial_files_from_database_parent() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("startup.sqlite3");
        LibraryStore::open(&database).unwrap();
        let partial = temp.path().join("orphan.resume.json");
        std::fs::write(&partial, b"resume").unwrap();

        let service =
            FfiStartupReconciliationService::open(database.to_string_lossy().into_owned()).unwrap();
        let result = service.startup_reconcile();
        assert!(result.error.is_none());
        assert_eq!(result.orphaned_partial_files_removed, 1);
        assert!(!partial.exists());
    }

    #[test]
    fn ffi_startup_reconciliation_result_uses_safe_error_shape() {
        let error = CoreError::new(ErrorKind::Persistence, "database unavailable", true);
        let ffi = FfiStartupReconciliationResult {
            jobs_requeued: 0,
            orphaned_staged_jobs_recovered: 0,
            asset_mismatches_reported: 0,
            orphaned_partial_files_removed: 0,
            error: Some(FfiError::from(&error)),
        };
        assert_eq!(ffi.error.as_ref().unwrap().message, "database unavailable");
        assert!(ffi.error.as_ref().unwrap().retryable);
    }
}
