use crate::{
    DownloadPolicy, DownloadWorkItem, DownloadWorker, DurableDownloadWorkStore,
    FfiCoreServiceOpenError, FfiError, LibraryStore,
};
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadWorkerResult {
    pub executed: bool,
    pub completed: bool,
    pub paused: bool,
    pub failed: bool,
    pub retry_wait: bool,
    pub canceled: bool,
    pub error: Option<FfiError>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadPresentation {
    pub job_id: String,
    pub display_title: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadPresentationResult {
    pub items: Vec<FfiDownloadPresentation>,
    pub error: Option<FfiError>,
}

#[derive(Debug, uniffi::Object)]
pub struct FfiDownloadWorkerService {
    database_path: PathBuf,
    library_root: PathBuf,
}

#[uniffi::export]
impl FfiDownloadWorkerService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiCoreServiceOpenError> {
        let database_path = PathBuf::from(database_path);
        LibraryStore::open(&database_path).map_err(|error| {
            FfiCoreServiceOpenError::Persistence { message: error.message }
        })?;
        DurableDownloadWorkStore::open(&database_path).map_err(|error| {
            FfiCoreServiceOpenError::Persistence { message: error.message }
        })?;
        let library_root = database_path
            .parent()
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from("."));
        Ok(Arc::new(Self { database_path, library_root }))
    }

    pub fn execute_job(
        &self,
        job_id: String,
        now_epoch_ms: u64,
        max_concurrent_downloads: u64,
    ) -> FfiDownloadWorkerResult {
        match self.execute_job_inner(&job_id, now_epoch_ms, max_concurrent_downloads) {
            Ok(result) => result,
            Err(error) => FfiDownloadWorkerResult {
                executed: false,
                completed: false,
                paused: false,
                failed: false,
                retry_wait: false,
                canceled: false,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn download_presentations(&self) -> FfiDownloadPresentationResult {
        match DurableDownloadWorkStore::open(&self.database_path)
            .and_then(|store| store.load_presentations())
        {
            Ok(items) => FfiDownloadPresentationResult {
                items: items
                    .into_iter()
                    .map(|(job_id, display_title)| FfiDownloadPresentation { job_id, display_title })
                    .collect(),
                error: None,
            },
            Err(error) => FfiDownloadPresentationResult {
                items: Vec::new(),
                error: Some(FfiError::from(&error)),
            },
        }
    }
}

impl FfiDownloadWorkerService {
    fn execute_job_inner(
        &self,
        job_id: &str,
        now_epoch_ms: u64,
        max_concurrent_downloads: u64,
    ) -> Result<FfiDownloadWorkerResult, crate::CoreError> {
        let work_store = DurableDownloadWorkStore::open(&self.database_path)?;
        let Some(work) = work_store
            .load_all()?
            .into_iter()
            .find(|work| work.job_id == job_id)
        else {
            return Err(crate::CoreError::new(
                crate::ErrorKind::InvalidInput,
                "durable download work item does not exist",
                false,
            ));
        };

        let worker = DownloadWorker::new(
            LibraryStore::open(&self.database_path)?,
            &self.library_root,
            DownloadPolicy::default(),
            usize::try_from(max_concurrent_downloads).unwrap_or(usize::MAX),
        );
        let cancel = AtomicBool::new(false);
        let report = worker.execute_ready_at(
            &[DownloadWorkItem {
                job_id: work.job_id.clone(),
                plan: work.plan,
                created_at_epoch_ms: work.created_at_epoch_ms,
            }],
            &cancel,
            now_epoch_ms,
        )?;
        let completed = report.completed.iter().any(|id| id == job_id);
        let canceled = report.canceled.iter().any(|id| id == job_id);
        if completed || canceled {
            work_store.delete(job_id)?;
        }
        Ok(FfiDownloadWorkerResult {
            executed: report.claimed.iter().any(|id| id == job_id),
            completed,
            paused: report.paused.iter().any(|id| id == job_id),
            failed: report.failed.iter().any(|id| id == job_id),
            retry_wait: report.retry_wait.iter().any(|id| id == job_id),
            canceled,
            error: None,
        })
    }
}
