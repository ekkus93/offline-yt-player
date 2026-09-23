use crate::{
    Compatibility, CoreError, DownloadPlan, DownloadPlanAsset, DownloadPolicy, DownloadState,
    DownloadWorkItem, DownloadWorker, DurableDownloadSnapshot, ErrorKind, LibraryStore, MediaKind,
    QualityChoice, SourceIdentity, reconcile_startup_downloads,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
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
                    .respond(
                        TinyResponse::from_string("status fixture")
                            .with_status_code(StatusCode(status)),
                    )
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

fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
    DurableDownloadSnapshot {
        job_id: job_id.into(),
        state,
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
fn worker_max_attempts_one_makes_retryable_failure_terminal() {
    let server = StatusFixtureServer::start(503);
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("one-attempt", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        max_attempts: 1,
        ..DownloadPolicy::default()
    };
    let worker = DownloadWorker::new(store.clone(), root.path(), policy, 1);

    let report = worker
        .execute_ready_at(
            &[work_item(
                "one-attempt",
                format!("{}/server-error.mp4", server.address),
            )],
            &AtomicBool::new(false),
            20_000,
        )
        .unwrap();

    assert_eq!(report.failed, vec!["one-attempt"]);
    assert!(report.retry_wait.is_empty());
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Failed);
    assert_eq!(durable.attempt, 1);
    assert_eq!(durable.retry_at_epoch_ms, None);
    assert!(durable.last_error.unwrap().retryable);
}

#[test]
fn retry_wait_is_not_reclaimed_until_next_eligible_deadline() {
    let server = StatusFixtureServer::start(503);
    let store = LibraryStore::open_in_memory().unwrap();
    let mut retrying = snapshot("future-retry", DownloadState::RetryWait);
    retrying.attempt = 1;
    retrying.retry_at_epoch_ms = Some(90_000);
    retrying.last_error = Some(CoreError::new(
        ErrorKind::NetworkTimeout,
        "previous transient failure",
        true,
    ));
    store.save_download_snapshot(&retrying).unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[work_item(
                "future-retry",
                format!("{}/not-yet.mp4", server.address),
            )],
            &AtomicBool::new(false),
            89_999,
        )
        .unwrap();

    assert!(report.claimed.is_empty());
    assert!(report.retry_wait.is_empty());
    assert_eq!(store.load_download_snapshots().unwrap(), vec![retrying]);
}

#[test]
fn retry_wait_survives_startup_reconciliation_without_attempt_drift() {
    let store = LibraryStore::open_in_memory().unwrap();
    let mut retrying = snapshot("process-death-retry", DownloadState::RetryWait);
    retrying.attempt = 2;
    retrying.retry_at_epoch_ms = Some(123_456);
    retrying.last_error = Some(CoreError::new(
        ErrorKind::NetworkUnavailable,
        "network was unavailable",
        true,
    ));
    store.save_download_snapshot(&retrying).unwrap();

    let reconciliation = reconcile_startup_downloads(&store).unwrap();

    assert_eq!(reconciliation.jobs_requeued, 0);
    assert_eq!(store.load_download_snapshots().unwrap(), vec![retrying]);
}

#[test]
fn retry_wait_becomes_eligible_at_its_persisted_deadline() {
    let server = StatusFixtureServer::start(503);
    let store = LibraryStore::open_in_memory().unwrap();
    let mut retrying = snapshot("eligible-retry", DownloadState::RetryWait);
    retrying.attempt = 1;
    retrying.retry_at_epoch_ms = Some(50_000);
    retrying.last_error = Some(CoreError::new(
        ErrorKind::NetworkTimeout,
        "previous transient failure",
        true,
    ));
    store.save_download_snapshot(&retrying).unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[work_item(
                "eligible-retry",
                format!("{}/eligible.mp4", server.address),
            )],
            &AtomicBool::new(false),
            50_000,
        )
        .unwrap();

    assert_eq!(report.claimed, vec!["eligible-retry"]);
    assert_eq!(report.retry_wait, vec!["eligible-retry"]);
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::RetryWait);
    assert_eq!(durable.attempt, 2);
    assert!(durable.retry_at_epoch_ms.unwrap() > 50_000);
}
