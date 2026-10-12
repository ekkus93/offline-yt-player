//! Cross-connection regression: a platform stop must never undo an explicit
//! durable user pause/cancel while the original native transfer is active.
use offline_yt_core::{
    Compatibility, DownloadPlan, DownloadPlanAsset, DownloadPolicy, DownloadState,
    DownloadWorkItem, DownloadWorker, DurableDownloadSnapshot, LibraryStore, MediaKind,
    QualityChoice, SourceIdentity,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;
use tiny_http::{Response, Server};

fn snapshot(job_id: &str) -> DurableDownloadSnapshot {
    DurableDownloadSnapshot {
        job_id: job_id.into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    }
}

fn work(job_id: &str, url: String, size: usize) -> DownloadWorkItem {
    DownloadWorkItem {
        job_id: job_id.into(),
        created_at_epoch_ms: 1_000,
        plan: DownloadPlan {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: job_id.into(),
                canonical_url: None,
            },
            title: "UIDT stop race".into(),
            duration_ms: Some(60_000),
            quality: QualityChoice {
                choice_id: "fixture".into(),
                label: "720p".into(),
                estimated_bytes: Some(size as u64),
                video_height: Some(720),
                audio_only: false,
                compatibility: Compatibility::Preferred,
            },
            assets: vec![DownloadPlanAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                url,
                relative_path: format!("items/{job_id}/video.mp4"),
                expected_bytes: Some(size as u64),
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
        },
    }
}

#[test]
fn platform_stop_preserves_concurrent_user_pause_and_cancel() {
    for requested_state in [DownloadState::Paused, DownloadState::Canceled] {
        let server = Server::http("127.0.0.1:0").unwrap();
        let url = format!("http://{}/video.mp4", server.server_addr());
        let payload = vec![0x5a_u8; 4 * 1024 * 1024];
        let server_thread = thread::spawn(move || {
            if let Some(request) = server.recv_timeout(Duration::from_secs(10)).unwrap() {
                thread::sleep(Duration::from_millis(250));
                request.respond(Response::from_data(payload)).unwrap();
            }
        });
        let store = LibraryStore::open_in_memory().unwrap();
        let job_id = format!("uidt-stop-{requested_state:?}");
        store.save_download_snapshot(&snapshot(&job_id)).unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let stop = Arc::new(AtomicBool::new(false));
        let controller_stop = Arc::clone(&stop);
        let controller_store = store.clone();
        let controller_job = job_id.clone();
        let controller = thread::spawn(move || {
            for _ in 0..200 {
                let current = controller_store
                    .load_download_snapshots()
                    .unwrap()
                    .into_iter()
                    .find(|row| row.job_id == controller_job)
                    .unwrap();
                if current.state == DownloadState::Downloading {
                    let mut updated = current.clone();
                    updated.state = requested_state;
                    if controller_store
                        .replace_download_snapshot_if_unchanged(&current, &updated)
                        .unwrap()
                    {
                        controller_stop.store(true, Ordering::Release);
                        return;
                    }
                }
                thread::sleep(Duration::from_millis(5));
            }
            panic!("fixture did not reach Downloading");
        });
        let report = worker
            .execute_ready_with_platform_stop_at(
                &[work(&job_id, url, 4 * 1024 * 1024)],
                &AtomicBool::new(false),
                &stop,
                10_000,
            )
            .unwrap();
        controller.join().unwrap();
        server_thread.join().unwrap();
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, requested_state);
        assert_eq!(durable.retry_at_epoch_ms, None);
        assert!(report.retry_wait.is_empty());
        assert!(report.completed.is_empty());
        assert!(store.get(&job_id).unwrap().is_none());
    }
}
