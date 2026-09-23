use crate::{
    Compatibility, CoreError, DownloadPlan, DownloadPlanAsset, DownloadPolicy, DownloadState,
    DownloadWorkItem, DownloadWorker, ErrorKind, LibraryStore, MediaKind, QualityChoice,
    SourceIdentity, execute_with_retry,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::thread;
use std::time::Duration;
use tiny_http::{Response as TinyResponse, Server, StatusCode};

struct StatusFixtureServer {
    address: String,
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl StatusFixtureServer {
    fn start(status: u16) -> Self {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let stop = Arc::new(AtomicBool::new(false));
        let stop_thread = Arc::clone(&stop);
        let handle = thread::spawn(move || {
            while !stop_thread.load(Ordering::Relaxed) {
                let Ok(Some(request)) = server.recv_timeout(Duration::from_millis(50)) else {
                    continue;
                };
                request
                    .respond(TinyResponse::from_string("status fixture").with_status_code(StatusCode(status)))
                    .unwrap();
            }
        });
        Self {
            address,
            stop,
            handle: Some(handle),
        }
    }
}

impl Drop for StatusFixtureServer {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.join().unwrap();
        }
    }
}

fn queued_snapshot(job_id: &str) -> crate::DurableDownloadSnapshot {
    crate::DurableDownloadSnapshot {
        job_id: job_id.into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    }
}

fn work_item(job_id: &str, url: String) -> DownloadWorkItem {
    DownloadWorkItem {
        job_id: job_id.into(),
        created_at_epoch_ms: 1_000,
        plan: DownloadPlan {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: job_id.into(),
                canonical_url: Some(format!("https://fixture.invalid/{job_id}")),
            },
            title: format!("Video {job_id}"),
            quality: QualityChoice {
                choice_id: "fixture".into(),
                label: "Fixture".into(),
                estimated_bytes: None,
                video_height: Some(720),
                audio_only: false,
                compatibility: Compatibility::Preferred,
            },
            assets: vec![DownloadPlanAsset {
                asset_id: "combined".into(),
                kind: MediaKind::Video,
                url,
                relative_path: format!("items/{job_id}/video.mp4"),
                expected_bytes: None,
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
        },
    }
}

#[test]
fn worker_treats_http_404_as_terminal_nonretryable_failure() {
    let server = StatusFixtureServer::start(404);
    let store = LibraryStore::open_in_memory().unwrap();
    store.save_download_snapshot(&queued_snapshot("not-found")).unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[work_item("not-found", format!("{}/missing.mp4", server.address))],
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap();

    assert_eq!(report.failed, vec!["not-found"]);
    assert!(report.retry_wait.is_empty());
    let snapshot = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(snapshot.state, DownloadState::Failed);
    assert_eq!(snapshot.attempt, 1);
    assert_eq!(snapshot.retry_at_epoch_ms, None);
    let error = snapshot.last_error.unwrap();
    assert_eq!(error.kind, ErrorKind::HttpStatus);
    assert!(!error.retryable);
}

#[test]
fn worker_treats_http_503_as_retryable_transient_failure() {
    let server = StatusFixtureServer::start(503);
    let store = LibraryStore::open_in_memory().unwrap();
    store.save_download_snapshot(&queued_snapshot("server-error")).unwrap();
    let root = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        max_attempts: 4,
        ..DownloadPolicy::default()
    };
    let worker = DownloadWorker::new(store.clone(), root.path(), policy, 1);

    let report = worker
        .execute_ready_at(
            &[work_item("server-error", format!("{}/retry.mp4", server.address))],
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap();

    assert_eq!(report.retry_wait, vec!["server-error"]);
    assert!(report.failed.is_empty());
    let snapshot = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(snapshot.state, DownloadState::RetryWait);
    assert_eq!(snapshot.attempt, 1);
    assert!(snapshot.retry_at_epoch_ms.unwrap() > 10_000);
    let error = snapshot.last_error.unwrap();
    assert_eq!(error.kind, ErrorKind::HttpStatus);
    assert!(error.retryable);
}

#[test]
fn nonretryable_source_changed_error_is_never_promoted_by_kind() {
    let calls = AtomicU32::new(0);
    let error = execute_with_retry(4, 0, &AtomicBool::new(false), |_| {
        calls.fetch_add(1, Ordering::Relaxed);
        Err::<(), _>(CoreError::new(
            ErrorKind::SourceChanged,
            "provider response changed",
            false,
        ))
    })
    .unwrap_err();

    assert_eq!(error.kind, ErrorKind::SourceChanged);
    assert!(!error.retryable);
    assert_eq!(calls.load(Ordering::Relaxed), 1);
}
