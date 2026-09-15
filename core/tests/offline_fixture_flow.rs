use futures::executor::block_on;
use offline_yt_core::{
    Compatibility, DirectFixtureSource, DownloadEngine, DownloadPolicy, DurableDownloadSnapshot,
    ErrorKind, FixtureMedia, LibraryItem, LibraryStore, LocalAsset, MediaSource, SourceRegistry,
    TransferRequest,
};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;
use tiny_http::{Header, Response, Server, StatusCode};

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
                let Ok(Some(request)) = server.recv_timeout(Duration::from_millis(50)) else {
                    continue;
                };
                let range = request
                    .headers()
                    .iter()
                    .find(|header| header.field.equiv("Range"))
                    .map(|header| header.value.as_str().to_owned());
                if let Some(range) = range {
                    let start = range
                        .strip_prefix("bytes=")
                        .and_then(|value| value.strip_suffix('-'))
                        .and_then(|value| value.parse::<usize>().ok())
                        .unwrap_or(0)
                        .min(body.len());
                    let slice = body[start..].to_vec();
                    let mut response = Response::from_data(slice).with_status_code(StatusCode(206));
                    response.add_header(
                        Header::from_bytes(
                            b"Content-Range".as_slice(),
                            format!(
                                "bytes {start}-{}/{}",
                                body.len().saturating_sub(1),
                                body.len()
                            ),
                        )
                        .unwrap(),
                    );
                    request.respond(response).unwrap();
                } else {
                    request.respond(Response::from_data(body.clone())).unwrap();
                }
            }
        });
        Self {
            address,
            stop,
            handle: Some(handle),
        }
    }

    fn url(&self, path: &str) -> String {
        format!("{}{path}", self.address)
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

#[test]
fn deterministic_fixture_flow_survives_restart_and_reconstructs_offline() {
    let media_bytes = b"offline playable fixture media segment".repeat(512);
    let server = FixtureServer::start(media_bytes.clone());
    let root = tempfile::tempdir().unwrap();
    let library_root = root.path().join("library");
    let db_path = root.path().join("library.sqlite");
    let source_url = "https://fixture.invalid/watch/e2e-one";
    let fixture_source = Arc::new(DirectFixtureSource::with_entries([(
        source_url.to_owned(),
        FixtureMedia {
            media_id: "e2e-one".into(),
            title: "  E2E\nFixture  ".into(),
            duration_ms: 180_000,
            media_url: server.url("/media/e2e-one.mp4"),
            thumbnail_url: None,
            bytes: Some(media_bytes.len() as u64),
        },
    )]));
    let mut registry = SourceRegistry::new();
    registry.register(fixture_source);

    let source = registry.select(source_url).unwrap();
    let media = block_on(source.resolve(source_url)).unwrap();
    assert_eq!(media.title, "E2E Fixture");
    let choices = block_on(source.choices(&media)).unwrap();
    assert_eq!(choices.len(), 1);
    assert_eq!(choices[0].compatibility, Compatibility::Preferred);
    let plan = block_on(source.download_plan(&media, &choices[0].choice_id)).unwrap();
    let plan_asset = plan.assets.first().unwrap();

    let final_path = library_root.join(&plan_asset.relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    fs::write(partial_path(&final_path), &media_bytes[..256]).unwrap();

    let store_before_restart = LibraryStore::open(&db_path).unwrap();
    store_before_restart
        .save_download_snapshot(&DurableDownloadSnapshot {
            job_id: "job-e2e".into(),
            state: offline_yt_core::DownloadState::Downloading,
            bytes_downloaded: 256,
            total_bytes: Some(media_bytes.len() as u64),
            attempt: 1,
            retry_at_epoch_ms: None,
            last_error: None,
        })
        .unwrap();
    drop(store_before_restart);

    let store_after_restart = LibraryStore::open(&db_path).unwrap();
    assert_eq!(store_after_restart.load_download_snapshots().unwrap().len(), 1);

    let engine = DownloadEngine::new(&library_root, DownloadPolicy::default()).unwrap();
    engine
        .preflight_space(
            plan_asset.expected_bytes,
            Some(media_bytes.len() as u64 + 1024),
        )
        .unwrap();
    let transfer = engine
        .transfer(
            &TransferRequest {
                url: plan_asset.url.clone(),
                relative_path: plan_asset.relative_path.clone(),
                expected_bytes: plan_asset.expected_bytes,
                expected_sha256: plan_asset.expected_sha256.clone(),
            },
            &AtomicBool::new(false),
        )
        .unwrap();
    assert!(transfer.resumed);
    assert_eq!(fs::read(&final_path).unwrap(), media_bytes);

    let local_asset = LocalAsset {
        asset_id: plan_asset.asset_id.clone(),
        kind: plan_asset.kind,
        relative_path: transfer.relative_path.clone(),
        bytes: transfer.bytes,
        sha256: Some(transfer.sha256),
        mime_type: plan_asset.mime_type.clone(),
    };
    store_after_restart
        .stage_asset("job-e2e", &local_asset)
        .unwrap();
    store_after_restart
        .promote_completed(
            "job-e2e",
            &LibraryItem {
                item_id: "e2e-one".into(),
                source: plan.source.clone(),
                display_title: plan.title.clone(),
                duration_ms: media.duration_ms,
                quality_label: plan.quality.label.clone(),
                assets: vec![local_asset],
                created_at_epoch_ms: 1_700_000_000_000,
                playback_position_ms: 0,
                completed: true,
            },
        )
        .unwrap();
    drop(server);

    let offline_store = LibraryStore::open(&db_path).unwrap();
    let completed = offline_store.get("e2e-one").unwrap().unwrap();
    assert!(completed.completed);
    assert_eq!(completed.assets.len(), 1);
    offline_store
        .validate_item_assets(&library_root, "e2e-one")
        .unwrap();
    assert!(offline_store.list(Some("Fixture")).unwrap().len() == 1);
}

#[test]
fn deterministic_storage_failure_flow_has_recovery_signal() {
    let root = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .preflight_space(Some(4_096), Some(4_095))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    assert!(!error.retryable);
}

fn partial_path(final_path: &Path) -> PathBuf {
    let name = final_path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("asset");
    final_path.with_file_name(format!(".{name}.partial"))
}
