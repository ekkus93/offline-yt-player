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
            FfiCoreServiceOpenError::Persistence {
                details: error.message,
            }
        })?;
        DurableDownloadWorkStore::open(&database_path).map_err(|error| {
            FfiCoreServiceOpenError::Persistence {
                details: error.message,
            }
        })?;
        let library_root = database_path
            .parent()
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from("."));
        Ok(Arc::new(Self {
            database_path,
            library_root,
        }))
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
                    .map(|(job_id, display_title)| FfiDownloadPresentation {
                        job_id,
                        display_title,
                    })
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

#[cfg(test)]
mod asset_identity_tests {
    use super::*;
    use crate::{
        Compatibility, DownloadPlan, DownloadPlanAsset, DownloadState, DurableDownloadSnapshot,
        DurableDownloadWorkItem, ErrorKind, MediaKind, QualityChoice, SourceIdentity,
    };

    #[test]
    fn malformed_persisted_assets_settle_as_nonretryable_failure_through_ffi() {
        for defect in [
            "duplicate-id",
            "duplicate-path",
            "normalized-path",
            "blank-id",
            "blank-path",
            "traversal-path",
        ] {
            let workspace = tempfile::tempdir().expect("temporary file-backed database");
            let database_path = workspace.path().join("downloads.sqlite3");
            let job_id = format!("invalid-{defect}");
            let first = DownloadPlanAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                url: "http://127.0.0.1:1/unreachable".into(),
                relative_path: format!("items/{job_id}/video.mp4"),
                expected_bytes: Some(10),
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            };
            let mut second = first.clone();
            second.asset_id = "audio".into();
            second.relative_path = format!("items/{job_id}/audio.mp4");
            match defect {
                "duplicate-id" => second.asset_id = first.asset_id.clone(),
                "duplicate-path" => second.relative_path = first.relative_path.clone(),
                "normalized-path" => {
                    second.relative_path = format!("items/{job_id}/./video.mp4");
                }
                "blank-id" => second.asset_id = "  ".into(),
                "blank-path" => second.relative_path = "  ".into(),
                "traversal-path" => second.relative_path = "../escape.mp4".into(),
                _ => unreachable!(),
            }

            let library = LibraryStore::open(&database_path).expect("open library");
            library
                .save_download_snapshot(&DurableDownloadSnapshot {
                    job_id: job_id.clone(),
                    state: DownloadState::Queued,
                    bytes_downloaded: 0,
                    total_bytes: None,
                    attempt: 0,
                    retry_at_epoch_ms: None,
                    last_error: None,
                })
                .expect("persist queued job");
            let work_store = DurableDownloadWorkStore::open(&database_path).unwrap();
            work_store
                .save(&DurableDownloadWorkItem {
                    job_id: job_id.clone(),
                    created_at_epoch_ms: 1000,
                    plan: DownloadPlan {
                        source: SourceIdentity::new("fixture", &job_id),
                        title: "Invalid identity fixture".into(),
                        duration_ms: Some(1000),
                        quality: QualityChoice {
                            choice_id: "fixture".into(),
                            label: "720p".into(),
                            estimated_bytes: Some(20),
                            video_height: Some(720),
                            audio_only: false,
                            compatibility: Compatibility::Preferred,
                        },
                        assets: vec![first, second],
                    },
                })
                .expect("persist executable malformed work");
            let service =
                FfiDownloadWorkerService::open(database_path.to_string_lossy().into_owned())
                    .expect("open FFI worker");
            let result = service.execute_job(job_id.clone(), 10_000, 1);
            assert!(result.executed, "{defect}: {result:?}");
            assert!(result.failed, "{defect}: {result:?}");
            assert!(!result.completed && !result.retry_wait, "{defect}");
            assert!(result.error.is_none(), "{defect}: {result:?}");

            let durable = library.load_download_snapshots().unwrap();
            assert_eq!(durable.len(), 1, "{defect}");
            let snapshot = &durable[0];
            assert_eq!(snapshot.state, DownloadState::Failed, "{defect}");
            assert_eq!(snapshot.attempt, 1, "{defect}");
            assert_eq!(snapshot.bytes_downloaded, 0, "{defect}");
            let error = snapshot.last_error.as_ref().expect("durable error");
            assert_eq!(error.kind, ErrorKind::InvalidInput, "{defect}");
            assert!(!error.retryable, "{defect}");
            // A subsequent scheduler invocation must not reclaim malformed work.
            // The durable FAILED settlement is terminal until an explicit retry.
            let second_run = service.execute_job(job_id.clone(), 20_000, 1);
            assert!(!second_run.executed, "{defect}: {second_run:?}");
            assert!(!second_run.completed && !second_run.retry_wait, "{defect}");
            assert_eq!(library.load_download_snapshots().unwrap()[0].attempt, 1, "{defect}");
            assert!(library.list(None).unwrap().is_empty(), "{defect}");
            assert!(library.staged_job_ids().unwrap().is_empty(), "{defect}");
            assert!(
                !workspace.path().join(format!("items/{job_id}")).exists(),
                "{defect}"
            );
        }
    }
}
