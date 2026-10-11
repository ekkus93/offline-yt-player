use crate::{
    CoreError, DownloadState, DownloadStateMachine, DurableDownloadSnapshot,
    DurableDownloadWorkStore, ErrorKind, LibraryStore,
    build_download_work_if_supported_source_url_with_options,
};
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiDownloadControlOpenError {
    #[error("persistence error: {details}")]
    Persistence { details: String },
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
            .map(|library| {
                Arc::new(Self {
                    database_path,
                    library,
                })
            })
            .map_err(|error| FfiDownloadControlOpenError::Persistence {
                details: error.message,
            })
    }

    pub fn enqueue(&self, job_id: String) -> FfiDownloadControlResult {
        match self.enqueue_inner(&job_id, None, None, None) {
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

    pub fn enqueue_with_choice(
        &self,
        job_id: String,
        choice_id: String,
    ) -> FfiDownloadControlResult {
        match self.enqueue_inner(&job_id, Some(&choice_id), None, None) {
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

    pub fn enqueue_with_options(
        &self,
        job_id: String,
        choice_id: Option<String>,
        subtitle_track_id: Option<String>,
        audio_format_id: Option<String>,
    ) -> FfiDownloadControlResult {
        match self.enqueue_inner(
            &job_id,
            choice_id.as_deref(),
            subtitle_track_id.as_deref(),
            audio_format_id.as_deref(),
        ) {
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

    pub fn resume(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Queued)
    }

    pub fn cancel(&self, job_id: String) -> FfiDownloadControlResult {
        self.transition(&job_id, DownloadState::Canceled)
    }

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
    fn enqueue_inner(
        &self,
        job_id: &str,
        choice_id: Option<&str>,
        subtitle_track_id: Option<&str>,
        audio_format_id: Option<&str>,
    ) -> Result<bool, CoreError> {
        if job_id.trim().is_empty() {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "download job id must not be empty",
                false,
            ));
        }
        let snapshots = self.library.load_download_snapshots()?;
        if let Some(existing) = snapshots.iter().find(|snapshot| snapshot.job_id == job_id) {
            // A platform scheduler rejection occurs after the durable enqueue.
            // Repeated user enqueue may retry scheduling only for queued jobs
            // that still have executable work, without rewriting either row.
            if existing.state != DownloadState::Queued {
                return Ok(false);
            }
            return Ok(DurableDownloadWorkStore::open(&self.database_path)?
                .load_all()?
                .iter()
                .any(|work| work.job_id == job_id));
        }
        let executable_work = build_download_work_if_supported_source_url_with_options(
            job_id,
            choice_id,
            subtitle_track_id,
            audio_format_id,
        )?;
        match executable_work {
            Some(work) => {
                DurableDownloadWorkStore::open(&self.database_path)?.enqueue_if_absent(&work)
            }
            None => self
                .library
                .insert_download_snapshot_if_absent(&DurableDownloadSnapshot {
                    job_id: job_id.into(),
                    state: DownloadState::Queued,
                    bytes_downloaded: 0,
                    total_bytes: None,
                    attempt: 0,
                    retry_at_epoch_ms: None,
                    last_error: None,
                }),
        }
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
        let previous = snapshot.clone();
        let mut machine = DownloadStateMachine::new(snapshot.state);
        machine.transition(next)?;
        snapshot.state = machine.state();
        self.save_if_unchanged(&previous, snapshot)
    }

    /// Control requests race with worker progress, completion, and recovery.
    /// A stale read must never overwrite a newer durable job state.
    fn save_if_unchanged(
        &self,
        previous: &DurableDownloadSnapshot,
        next: &DurableDownloadSnapshot,
    ) -> Result<bool, CoreError> {
        if self
            .library
            .replace_download_snapshot_if_unchanged(previous, next)?
        {
            Ok(true)
        } else {
            Err(CoreError::new(
                ErrorKind::Internal,
                "download state changed during control action; refresh and retry",
                true,
            ))
        }
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
        let previous = snapshot.clone();
        let mut machine = DownloadStateMachine::new(snapshot.state);
        machine.transition(DownloadState::Queued)?;
        snapshot.state = machine.state();
        snapshot.attempt = 0;

        snapshot.retry_at_epoch_ms = None;
        snapshot.last_error = None;
        self.save_if_unchanged(&previous, snapshot)
    }
}

#[cfg(test)]
mod stale_control_tests {
    use super::*;

    fn fixture(state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: "competing-control".into(),
            state,
            bytes_downloaded: 42,
            total_bytes: Some(100),
            attempt: 2,
            retry_at_epoch_ms: None,
            last_error: None,
        }
    }

    #[test]
    fn queued_executable_work_can_retry_rejected_android_scheduling() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("scheduler-retry.sqlite3");
        let service = FfiDownloadControlService::open(path.to_string_lossy().into_owned()).unwrap();
        let queue = LibraryStore::open(&path).unwrap();
        let work_store = DurableDownloadWorkStore::open(&path).unwrap();
        let work = crate::DurableDownloadWorkItem {
            job_id: "retry-scheduler".into(),
            plan: crate::DownloadPlan {
                source: crate::SourceIdentity::new("fixture", "media-1"),
                title: "Fixture".into(),
                duration_ms: None,
                quality: crate::QualityChoice {
                    choice_id: "360p".into(),
                    label: "360p".into(),
                    estimated_bytes: Some(10),
                    video_height: Some(360),
                    audio_only: false,
                    compatibility: crate::Compatibility::Preferred,
                },
                assets: vec![crate::DownloadPlanAsset {
                    asset_id: "video".into(),
                    kind: crate::MediaKind::Video,
                    url: "http://127.0.0.1/fixture".into(),
                    relative_path: "items/retry-scheduler/video.mp4".into(),
                    expected_bytes: Some(10),
                    expected_sha256: None,
                    mime_type: Some("video/mp4".into()),
                }],
            },
            created_at_epoch_ms: 123,
        };
        assert!(work_store.enqueue_if_absent(&work).unwrap());
        let queued = queue.load_download_snapshots().unwrap().remove(0);
        assert!(service.enqueue("retry-scheduler".into()).updated);
        assert_eq!(
            queue.load_download_snapshots().unwrap(),
            vec![queued.clone()]
        );
        assert_eq!(work_store.load_all().unwrap(), vec![work]);

        let mut active = queued.clone();
        active.state = DownloadState::Downloading;
        queue.save_download_snapshot(&active).unwrap();
        assert!(!service.enqueue("retry-scheduler".into()).updated);

        let mut no_plan = queued;
        no_plan.job_id = "queue-only".into();
        queue.save_download_snapshot(&no_plan).unwrap();
        assert!(!service.enqueue("queue-only".into()).updated);
    }

    #[test]
    fn stale_control_cannot_overwrite_a_concurrent_pause_or_completion() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("control.sqlite3");
        let service = FfiDownloadControlService::open(path.to_string_lossy().into_owned()).unwrap();
        let other = LibraryStore::open(&path).unwrap();

        let original = fixture(DownloadState::Downloading);
        other.save_download_snapshot(&original).unwrap();
        let mut stale_cancel = original.clone();
        stale_cancel.state = DownloadState::Canceled;

        let mut paused = original.clone();
        paused.state = DownloadState::Paused;
        other.save_download_snapshot(&paused).unwrap();
        let conflict = service
            .save_if_unchanged(&original, &stale_cancel)
            .unwrap_err();
        assert!(conflict.retryable);
        assert_eq!(other.load_download_snapshots().unwrap(), vec![paused]);

        let verifying = fixture(DownloadState::Verifying);
        other.save_download_snapshot(&verifying).unwrap();
        let mut stale_pause = verifying.clone();
        stale_pause.state = DownloadState::Paused;
        let mut completed = verifying.clone();
        completed.state = DownloadState::Completed;
        other.save_download_snapshot(&completed).unwrap();
        assert!(service.save_if_unchanged(&verifying, &stale_pause).is_err());
        assert_eq!(other.load_download_snapshots().unwrap(), vec![completed]);
    }

    #[test]
    fn stale_retry_cannot_reset_an_already_reclaimed_job() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("retry.sqlite3");
        let service = FfiDownloadControlService::open(path.to_string_lossy().into_owned()).unwrap();
        let other = LibraryStore::open(&path).unwrap();
        let failed = fixture(DownloadState::Failed);
        other.save_download_snapshot(&failed).unwrap();
        let mut stale_retry = failed.clone();
        stale_retry.state = DownloadState::Queued;
        stale_retry.attempt = 0;

        let mut reclaimed = failed.clone();
        reclaimed.state = DownloadState::Resolving;
        reclaimed.attempt = 3;
        other.save_download_snapshot(&reclaimed).unwrap();

        assert!(service.save_if_unchanged(&failed, &stale_retry).is_err());
        assert_eq!(other.load_download_snapshots().unwrap(), vec![reclaimed]);
    }

    #[test]
    fn valid_controls_still_persist_through_atomic_update() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("valid.sqlite3");
        let service = FfiDownloadControlService::open(path.to_string_lossy().into_owned()).unwrap();
        let other = LibraryStore::open(&path).unwrap();
        other
            .save_download_snapshot(&fixture(DownloadState::Queued))
            .unwrap();
        assert!(service.pause("competing-control".into()).updated);
        assert_eq!(
            other.load_download_snapshots().unwrap()[0].state,
            DownloadState::Paused
        );
        assert!(service.resume("competing-control".into()).updated);
        assert!(service.cancel("competing-control".into()).updated);
        assert_eq!(
            other.load_download_snapshots().unwrap()[0].state,
            DownloadState::Canceled
        );
    }
}
