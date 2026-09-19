use crate::{
    Compatibility, DownloadPlan, DownloadPlanAsset, DownloadPolicy, DownloadState, DownloadWorkItem,
    DownloadWorker, DurableDownloadSnapshot, FfiDownloadControlService, LibraryStore, MediaKind,
    QualityChoice, SourceIdentity,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use tiny_http::{Response as TinyResponse, Server};

#[test]
fn durable_cancel_stops_active_work_and_persists_terminal_state() {
    let server = Server::http("127.0.0.1:0").unwrap();
    let address = format!("http://{}", server.server_addr());
    let stop_server = Arc::new(AtomicBool::new(false));
    let server_stop = Arc::clone(&stop_server);
    let server_thread = thread::spawn(move || {
        while !server_stop.load(Ordering::Relaxed) {
            let Ok(Some(request)) = server.recv_timeout(std::time::Duration::from_millis(50)) else {
                continue;
            };
            thread::sleep(std::time::Duration::from_millis(150));
            let _ = request.respond(TinyResponse::from_data(vec![9_u8; 4 * 1024 * 1024]));
        }
    });

    let temp = tempfile::tempdir().unwrap();
    let database = temp.path().join("library.sqlite3");
    let media_root = temp.path().join("media");
    let store = LibraryStore::open(&database).unwrap();
    store
        .save_download_snapshot(&DurableDownloadSnapshot {
            job_id: "cancel-job".into(),
            state: DownloadState::Queued,
            bytes_downloaded: 0,
            total_bytes: None,
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: None,
        })
        .unwrap();

    let work = DownloadWorkItem {
        job_id: "cancel-job".into(),
        created_at_epoch_ms: 1_000,
        plan: DownloadPlan {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "cancel-job".into(),
                canonical_url: None,
            },
            title: "Cancel fixture".into(),
            quality: QualityChoice {
                choice_id: "fixture".into(),
                label: "720p".into(),
                estimated_bytes: Some(4 * 1024 * 1024),
                video_height: Some(720),
                audio_only: false,
                compatibility: Compatibility::Preferred,
            },
            assets: vec![DownloadPlanAsset {
                asset_id: "combined".into(),
                kind: MediaKind::Video,
                url: format!("{address}/video.mp4"),
                relative_path: "items/cancel-job/video.mp4".into(),
                expected_bytes: Some(4 * 1024 * 1024),
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
        },
    };

    let controller_db = database.to_string_lossy().into_owned();
    let controller_store = store.clone();
    let controller = thread::spawn(move || {
        for _ in 0..100 {
            let current = controller_store
                .load_download_snapshots()
                .unwrap()
                .remove(0);
            if current.state == DownloadState::Downloading {
                let control = FfiDownloadControlService::open(controller_db).unwrap();
                let result = control.cancel("cancel-job".into());
                assert!(result.updated);
                assert!(result.error.is_none());
                return;
            }
            thread::sleep(std::time::Duration::from_millis(5));
        }
        panic!("worker never entered downloading state");
    });

    let worker = DownloadWorker::new(store.clone(), &media_root, DownloadPolicy::default(), 1);
    let report = worker
        .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
        .unwrap();
    controller.join().unwrap();
    stop_server.store(true, Ordering::Relaxed);
    server_thread.join().unwrap();

    assert_eq!(report.canceled, vec!["cancel-job"]);
    assert!(report.paused.is_empty());
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Canceled);
    assert!(durable.retry_at_epoch_ms.is_none());
    assert!(durable.last_error.is_none());
    assert!(!media_root.join("items/cancel-job/video.mp4").exists());
    // Default policy intentionally retains a resumable partial; terminal state prevents automatic
    // reuse unless a future explicit user action/policy chooses to recover it.
    assert!(
        media_root
            .join("items/cancel-job/.video.mp4.partial")
            .exists()
    );
}
