use crate::{
    DirectFixtureSource, DownloadPolicy, DownloadState, DownloadWorkItem, DownloadWorker,
    DurableDownloadSnapshot, FfiLibraryPlaybackService, FixtureMedia, LibraryStore, MediaKind,
    SourceRegistry,
};
use futures::executor::block_on;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::thread;
use tiny_http::{Header, Response, Server};

#[test]
fn offline_fixture_e2e_downloads_reopens_and_exports_local_playback_asset() {
    let workspace = tempfile::tempdir().expect("create fixture workspace");
    let database_path = workspace.path().join("offline-yt-e2e.sqlite3");
    let library_root = workspace.path().join("library-root");
    std::fs::create_dir_all(&library_root).expect("create library root");

    let media_bytes = b"deterministic offline fixture media bytes\n".to_vec();
    let expected_bytes = media_bytes.len() as u64;
    let (base_url, server_handle) = one_shot_fixture_server(media_bytes);
    let source_url = format!("{base_url}/fixture-source");
    let media_url = format!("{base_url}/media.mp4");

    let mut registry = SourceRegistry::new();
    registry.register(Arc::new(DirectFixtureSource::with_entries([(
        source_url.clone(),
        FixtureMedia {
            media_id: "rmd-1500-offline-fixture".into(),
            title: "RMD-1500 offline fixture".into(),
            duration_ms: 42_000,
            media_url,
            thumbnail_url: None,
            bytes: Some(expected_bytes),
        },
    )])));

    let selected_source = registry
        .select(&source_url)
        .expect("registered fixture source is selected");
    let media = block_on(selected_source.resolve(&source_url)).expect("fixture source resolves");
    let choices = block_on(selected_source.choices(&media)).expect("fixture quality choices resolve");
    let chosen = choices.first().expect("fixture exposes a curated quality choice");
    let plan = block_on(selected_source.download_plan(&media, &chosen.choice_id))
        .expect("fixture download plan resolves through source abstraction");

    let store = LibraryStore::open(&database_path).expect("open clean durable store");
    assert!(store.list(None).expect("list clean library").is_empty());
    assert!(store.load_download_snapshots().expect("list clean queue").is_empty());

    let job_id = "rmd-1500-offline-fixture-job".to_string();
    store
        .save_download_snapshot(&DurableDownloadSnapshot {
            job_id: job_id.clone(),
            state: DownloadState::Queued,
            bytes_downloaded: 0,
            total_bytes: None,
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: None,
        })
        .expect("persist queued fixture work");

    let cancel = AtomicBool::new(false);
    let worker = DownloadWorker::new(store.clone(), &library_root, DownloadPolicy::default(), 1);
    let report = worker
        .execute_ready_at(
            &[DownloadWorkItem {
                job_id: job_id.clone(),
                plan,
                created_at_epoch_ms: 1_700_000_000_000,
            }],
            &cancel,
            1_700_000_000_000,
        )
        .expect("worker completes deterministic fixture download");
    assert_eq!(report.completed, vec![job_id.clone()]);
    assert!(report.failed.is_empty(), "fixture work must not fail: {report:?}");

    server_handle.join().expect("fixture server handled exactly one media request");

    let reopened = LibraryStore::open(&database_path).expect("reopen durable store after cold start");
    let queue = reopened.load_download_snapshots().expect("reload durable queue");
    assert_eq!(queue.len(), 1);
    assert_eq!(queue[0].state, DownloadState::Completed);
    assert_eq!(queue[0].bytes_downloaded, expected_bytes);
    assert_eq!(queue[0].total_bytes, Some(expected_bytes));

    let items = reopened.list(None).expect("reload completed library");
    assert_eq!(items.len(), 1);
    let item = &items[0];
    assert_eq!(item.item_id, job_id);
    assert!(item.completed);
    assert_eq!(item.display_title, "RMD-1500 offline fixture");
    assert_eq!(item.quality_label, "720p");
    assert_eq!(item.assets.len(), 1);
    assert_eq!(item.assets[0].kind, MediaKind::Video);
    assert_eq!(item.assets[0].bytes, expected_bytes);
    reopened
        .validate_item_assets(&library_root, &item.item_id)
        .expect("completed fixture asset exists locally with the expected size");

    let playback_service = FfiLibraryPlaybackService::open(database_path.to_string_lossy().into_owned())
        .expect("open playback service from cold durable state");
    let playback_assets = playback_service.library_playback_assets();
    assert_eq!(playback_assets.error_message, None);
    assert_eq!(playback_assets.assets.len(), 1);
    let playback = &playback_assets.assets[0];
    assert_eq!(playback.item_id, item.item_id);
    assert!(playback.playable, "completed fixture must be locally playable: {playback:?}");
    assert_eq!(
        playback.video_relative_path.as_deref(),
        Some(item.assets[0].relative_path.as_str()),
    );
    assert!(playback.audio_relative_path.is_none());
}

fn one_shot_fixture_server(body: Vec<u8>) -> (String, thread::JoinHandle<()>) {
    let server = Server::http("127.0.0.1:0").expect("bind fixture server");
    let base_url = format!("{}://{}", "http", server.server_addr());
    let handle = thread::spawn(move || {
        let request = server.recv().expect("receive fixture media request");
        let content_type = Header::from_bytes(&b"Content-Type"[..], &b"video/mp4"[..])
            .expect("valid fixture content type header");
        request
            .respond(Response::from_data(body).with_header(content_type))
            .expect("serve fixture media response");
    });
    (base_url, handle)
}
