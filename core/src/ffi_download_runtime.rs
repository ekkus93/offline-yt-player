use crate::{
    CoreError, DownloadPolicy, DownloadWorkItem, DownloadWorker, DownloadWorkerReport, FfiError,
    LibraryStore,
};
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadWorkerReport {
    pub claimed: Vec<String>,
    pub completed: Vec<String>,
    pub paused: Vec<String>,
    pub failed: Vec<String>,
    pub retry_wait: Vec<String>,
    pub canceled: Vec<String>,
    pub repaired: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadWorkerResult {
    pub report: Option<FfiDownloadWorkerReport>,
    pub error: Option<FfiError>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiDownloadRuntimeOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, uniffi::Object)]
pub struct FfiDownloadRuntimeService {
    library: LibraryStore,
    library_root: PathBuf,
    cancel: AtomicBool,
}

#[uniffi::export]
impl FfiDownloadRuntimeService {
    #[uniffi::constructor]
    pub fn open(
        database_path: String,
        library_root: String,
    ) -> Result<Arc<Self>, FfiDownloadRuntimeOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self {
                library,
                library_root: PathBuf::from(library_root),
                cancel: AtomicBool::new(false),
            }))
            .map_err(|error| FfiDownloadRuntimeOpenError::Persistence { message: error.message })
    }

    pub fn run_ready(&self, now_epoch_ms: u64, max_concurrent: u32) -> FfiDownloadWorkerResult {
        self.cancel.store(false, Ordering::Release);
        match self.run_ready_inner(now_epoch_ms, max_concurrent) {
            Ok(report) => FfiDownloadWorkerResult {
                report: Some(FfiDownloadWorkerReport::from(&report)),
                error: None,
            },
            Err(error) => FfiDownloadWorkerResult {
                report: None,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn repair_interrupted(&self, now_epoch_ms: u64) -> FfiDownloadWorkerResult {
        let worker = DownloadWorker::new(
            self.library.clone(),
            self.library_root.clone(),
            DownloadPolicy::default(),
            1,
        );
        match worker.repair_interrupted_claims_at(now_epoch_ms) {
            Ok(report) => FfiDownloadWorkerResult {
                report: Some(FfiDownloadWorkerReport::from(&report)),
                error: None,
            },
            Err(error) => FfiDownloadWorkerResult {
                report: None,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::Release);
    }
}

impl FfiDownloadRuntimeService {
    fn run_ready_inner(
        &self,
        now_epoch_ms: u64,
        max_concurrent: u32,
    ) -> Result<DownloadWorkerReport, CoreError> {
        let work_items = self.library.load_download_work_items()?;
        let worker = DownloadWorker::new(
            self.library.clone(),
            self.library_root.clone(),
            DownloadPolicy::default(),
            usize::try_from(max_concurrent).unwrap_or(usize::MAX),
        );
        let report = worker.execute_ready_at(&work_items, &self.cancel, now_epoch_ms)?;
        for job_id in report.completed.iter().chain(report.canceled.iter()) {
            self.library.remove_download_work_item(job_id)?;
        }
        Ok(report)
    }
}

impl From<&DownloadWorkerReport> for FfiDownloadWorkerReport {
    fn from(report: &DownloadWorkerReport) -> Self {
        Self {
            claimed: report.claimed.clone(),
            completed: report.completed.clone(),
            paused: report.paused.clone(),
            failed: report.failed.clone(),
            retry_wait: report.retry_wait.clone(),
            canceled: report.canceled.clone(),
            repaired: report.repaired.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        Compatibility, DownloadPlan, DownloadPlanAsset, MediaKind, QualityChoice, SourceIdentity,
    };
    use std::thread;
    use tiny_http::{Response as TinyResponse, Server};

    fn work(job_id: &str, media_url: String, bytes: u64) -> DownloadWorkItem {
        DownloadWorkItem {
            job_id: job_id.into(),
            created_at_epoch_ms: 1,
            plan: DownloadPlan {
                source: SourceIdentity::new("fixture", job_id),
                title: "Runtime fixture".into(),
                quality: QualityChoice {
                    choice_id: "fixture-720p".into(),
                    label: "720p".into(),
                    estimated_bytes: Some(bytes),
                    video_height: Some(720),
                    audio_only: false,
                    compatibility: Compatibility::Preferred,
                },
                assets: vec![DownloadPlanAsset {
                    asset_id: "combined".into(),
                    kind: MediaKind::Video,
                    url: media_url,
                    relative_path: format!("items/{job_id}/video.mp4"),
                    expected_bytes: Some(bytes),
                    expected_sha256: None,
                    mime_type: Some("video/mp4".into()),
                }],
            },
        }
    }

    #[test]
    fn runtime_service_executes_durable_plan_after_database_reopen() {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let body = b"runtime-fixture".repeat(32);
        let expected_bytes = body.len() as u64;
        let server_thread = thread::spawn(move || {
            let request = server.recv().unwrap();
            request.respond(TinyResponse::from_data(body)).unwrap();
        });
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let library_root = temp.path().join("media");
        std::fs::create_dir_all(&library_root).unwrap();
        {
            let store = LibraryStore::open(&database).unwrap();
            assert!(store.enqueue_download_work_item(&work(
                "runtime-job",
                format!("{address}/media.mp4"),
                expected_bytes,
            )).unwrap());
        }
        let service = FfiDownloadRuntimeService::open(
            database.to_string_lossy().into_owned(),
            library_root.to_string_lossy().into_owned(),
        ).unwrap();
        let result = service.run_ready(10_000, 1);
        assert!(result.error.is_none());
        assert_eq!(result.report.unwrap().completed, vec!["runtime-job"]);
        assert!(library_root.join("items/runtime-job/video.mp4").is_file());
        let reopened = LibraryStore::open(&database).unwrap();
        assert!(reopened.get("runtime-job").unwrap().unwrap().completed);
        assert!(reopened.load_download_work_items().unwrap().is_empty());
        server_thread.join().unwrap();
    }
}
