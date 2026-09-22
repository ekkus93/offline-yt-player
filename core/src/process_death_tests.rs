use crate::{
    Compatibility, DownloadPlan, DownloadPlanAsset, DownloadPolicy, DownloadState,
    DownloadWorkItem, DownloadWorker, DurableDownloadSnapshot, LibraryStore, MediaKind,
    QualityChoice, SourceIdentity, reconcile_startup_with_library_root,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use tiny_http::{Response as TinyResponse, Server};

struct FixtureServer {
    address: String,
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl FixtureServer {
    fn start(body: Vec<u8>) -> Self {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let stop = Arc::new(AtomicBool::new(false));
        let stop_thread = Arc::clone(&stop);
        let handle = thread::spawn(move || {
            while !stop_thread.load(Ordering::Relaxed) {
                let Ok(Some(request)) = server.recv_timeout(std::time::Duration::from_millis(50))
                else {
                    continue;
                };
                request
                    .respond(TinyResponse::from_data(body.clone()))
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

impl Drop for FixtureServer {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.join().unwrap();
        }
    }
}

fn interrupted_snapshot(job_id: &str, total_bytes: u64) -> DurableDownloadSnapshot {
    DurableDownloadSnapshot {
        job_id: job_id.into(),
        state: DownloadState::Downloading,
        bytes_downloaded: 4,
        total_bytes: Some(total_bytes),
        attempt: 1,
        retry_at_epoch_ms: None,
        last_error: None,
    }
}

fn plan(job_id: &str, url: String, bytes: usize) -> DownloadWorkItem {
    DownloadWorkItem {
        job_id: job_id.into(),
        created_at_epoch_ms: 10_000,
        plan: DownloadPlan {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: job_id.into(),
                canonical_url: Some(format!("https://fixture.invalid/{job_id}")),
            },
            title: format!("Recovered {job_id}"),
            quality: QualityChoice {
                choice_id: "fixture".into(),
                label: "fixture".into(),
                estimated_bytes: Some(bytes as u64),
                video_height: Some(720),
                audio_only: false,
                compatibility: Compatibility::Preferred,
            },
            assets: vec![DownloadPlanAsset {
                asset_id: "combined".into(),
                kind: MediaKind::Video,
                url,
                relative_path: format!("items/{job_id}/video.mp4"),
                expected_bytes: Some(bytes as u64),
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
        },
    }
}

#[test]
fn process_death_relaunch_reconstructs_queue_and_completes_one_fixture_item() {
    let data = b"process death deterministic fixture".repeat(64);
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let database = temp.path().join("library.sqlite3");
    let media_root = temp.path().join("media");
    std::fs::create_dir_all(media_root.join("items/death-job")).unwrap();
    let partial_path = media_root.join("items/death-job/.video.mp4.partial");
    std::fs::write(&partial_path, b"part").unwrap();

    {
        let store = LibraryStore::open(&database).unwrap();
        store
            .save_download_snapshot(&interrupted_snapshot("death-job", data.len() as u64))
            .unwrap();
    }

    let relaunched = LibraryStore::open(&database).unwrap();
    let reconciliation = reconcile_startup_with_library_root(&relaunched, &media_root).unwrap();
    assert_eq!(reconciliation.jobs_requeued, 1);
    assert_eq!(reconciliation.orphaned_partial_files_removed, 0);
    let reconstructed = relaunched.load_download_snapshots().unwrap().remove(0);
    assert_eq!(reconstructed.job_id, "death-job");
    assert_eq!(reconstructed.state, DownloadState::Queued);
    assert_eq!(reconstructed.bytes_downloaded, 4);
    assert!(reconstructed.last_error.unwrap().retryable);
    assert!(partial_path.is_file());

    let worker = DownloadWorker::new(
        relaunched.clone(),
        &media_root,
        DownloadPolicy::default(),
        1,
    );
    let report = worker
        .execute_ready_at(
            &[plan(
                "death-job",
                format!("{}/fixture.mp4", server.address),
                data.len(),
            )],
            &AtomicBool::new(false),
            20_000,
        )
        .unwrap();
    assert_eq!(report.completed, vec!["death-job"]);

    drop(relaunched);
    let reopened = LibraryStore::open(&database).unwrap();
    let item = reopened.get("death-job").unwrap().unwrap();
    assert!(item.completed);
    assert_eq!(item.assets.len(), 1);
    assert_eq!(item.assets[0].bytes, data.len() as u64);
    reopened
        .validate_item_assets(&media_root, "death-job")
        .unwrap();
    let final_snapshot = reopened.load_download_snapshots().unwrap().remove(0);
    assert_eq!(final_snapshot.state, DownloadState::Completed);
    assert_eq!(final_snapshot.bytes_downloaded, data.len() as u64);
    assert!(final_snapshot.last_error.is_none());
    assert_eq!(reopened.list(None).unwrap().len(), 1);
    assert!(!partial_path.exists());

    let cleanup = reconcile_startup_with_library_root(&reopened, &media_root).unwrap();
    assert_eq!(cleanup.orphaned_partial_files_removed, 0);
}
