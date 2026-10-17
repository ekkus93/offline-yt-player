
use super::*;
use crate::domain::MediaKind;
use tempfile::tempdir;

fn item(completed: bool) -> LibraryItem {
    LibraryItem {
        item_id: "item-1".into(),
        source: SourceIdentity {
            provider: "fixture".into(),
            media_id: "source-1".into(),
            canonical_url: Some("https://fixture.invalid/1".into()),
        },
        display_title: "Test Video".into(),
        duration_ms: Some(60_000),
        quality_label: "720p".into(),
        assets: vec![LocalAsset {
            asset_id: "combined".into(),
            kind: MediaKind::Video,
            relative_path: "items/item-1/video.mp4".into(),
            bytes: 100,
            sha256: Some("abc".into()),
            mime_type: Some("video/mp4".into()),
        }],
        created_at_epoch_ms: 10,
        playback_position_ms: 0,
        completed,
    }
}

#[test]
fn fresh_database_migrates_to_current_schema() {
    let store = LibraryStore::open_in_memory().unwrap();
    assert_eq!(store.schema_version().unwrap(), SCHEMA_VERSION);
}

#[test]
fn promotion_is_atomic_and_searchable() {
    let store = LibraryStore::open_in_memory().unwrap();
    let complete = item(true);
    store.stage_asset("job-1", &complete.assets[0]).unwrap();
    store.promote_completed("job-1", &complete).unwrap();
    assert!(store.staged_job_ids().unwrap().is_empty());
    let found = store.list(Some("Video")).unwrap();
    assert_eq!(found, vec![complete]);
}

#[test]
fn concurrent_cancel_cannot_promote_or_complete_a_stale_job() {
    let store = LibraryStore::open_in_memory().unwrap();
    let complete = item(true);
    let verifying = DurableDownloadSnapshot {
        job_id: complete.item_id.clone(),
        state: DownloadState::Verifying,
        bytes_downloaded: 100,
        total_bytes: Some(100),
        attempt: 1,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    store.save_download_snapshot(&verifying).unwrap();
    store
        .stage_asset(&complete.item_id, &complete.assets[0])
        .unwrap();
    let mut canceled = verifying.clone();
    canceled.state = DownloadState::Canceled;
    store.save_download_snapshot(&canceled).unwrap();
    let mut completed = verifying.clone();
    completed.state = DownloadState::Completed;

    assert!(
        !store
            .promote_completed_if_unchanged(&complete.item_id, &complete, &verifying, &completed,)
            .unwrap()
    );
    assert_eq!(store.load_download_snapshots().unwrap(), vec![canceled]);
    assert!(store.list(None).unwrap().is_empty());
    assert_eq!(
        store.staged_job_ids().unwrap(),
        vec![complete.item_id.clone()]
    );

    store.save_download_snapshot(&verifying).unwrap();
    assert!(
        store
            .promote_completed_if_unchanged(&complete.item_id, &complete, &verifying, &completed,)
            .unwrap()
    );
    assert_eq!(store.load_download_snapshots().unwrap(), vec![completed]);
    assert_eq!(store.list(None).unwrap(), vec![complete]);
    assert!(store.staged_job_ids().unwrap().is_empty());
}

#[test]
fn incomplete_item_cannot_be_promoted() {
    let store = LibraryStore::open_in_memory().unwrap();
    let error = store.promote_completed("job-1", &item(false)).unwrap_err();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    assert!(store.list(None).unwrap().is_empty());
}

#[test]
fn completed_items_require_playable_media_for_promotion() {
    let store = LibraryStore::open_in_memory().unwrap();
    let mut empty = item(true);
    empty.assets.clear();
    assert_eq!(
        store.promote_completed("job-1", &empty).unwrap_err().kind,
        ErrorKind::IntegrityFailure
    );
    let mut thumbnails = item(true);
    thumbnails.assets[0].kind = MediaKind::Thumbnail;
    assert_eq!(
        store
            .promote_completed("job-1", &thumbnails)
            .unwrap_err()
            .kind,
        ErrorKind::IntegrityFailure
    );
    assert!(store.list(None).unwrap().is_empty());
}

#[test]
fn playback_rename_and_delete_round_trip() {
    let store = LibraryStore::open_in_memory().unwrap();
    store.promote_completed("job-1", &item(true)).unwrap();
    assert!(store.rename("item-1", "Renamed").unwrap());
    assert!(store.save_playback_position("item-1", 12_345).unwrap());
    let updated = store.get("item-1").unwrap().unwrap();
    assert_eq!(updated.display_title, "Renamed");
    assert_eq!(updated.playback_position_ms, 12_345);
    let deleted = store.delete("item-1").unwrap().unwrap();
    assert_eq!(deleted.item_id, "item-1");
    assert!(store.get("item-1").unwrap().is_none());
}

#[test]
fn durable_claim_compare_and_swap_rejects_stale_cross_connection_state() {
    let dir = tempdir().unwrap();
    let path = dir.path().join("claims.sqlite3");
    let worker_a = LibraryStore::open(&path).unwrap();
    let worker_b = LibraryStore::open(&path).unwrap();
    let queued = DurableDownloadSnapshot {
        job_id: "shared-job".into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    worker_a.save_download_snapshot(&queued).unwrap();
    let stale = worker_b.load_download_snapshots().unwrap().remove(0);

    let mut first_claim = queued.clone();
    first_claim.state = DownloadState::Resolving;
    first_claim.attempt = 1;
    assert!(
        worker_a
            .replace_download_snapshot_if_unchanged(&queued, &first_claim)
            .unwrap()
    );
    assert!(
        !worker_b
            .replace_download_snapshot_if_unchanged(&stale, &first_claim)
            .unwrap()
    );
    assert_eq!(
        worker_b.load_download_snapshots().unwrap(),
        vec![first_claim.clone()]
    );

    // A pause racing a second claim must not be reverted to Resolving.
    let mut paused = first_claim.clone();
    paused.state = DownloadState::Paused;
    worker_b.save_download_snapshot(&paused).unwrap();
    assert!(
        !worker_a
            .replace_download_snapshot_if_unchanged(&first_claim, &queued)
            .unwrap()
    );
    assert_eq!(worker_a.load_download_snapshots().unwrap(), vec![paused]);
}

#[test]
fn queue_only_enqueue_preserves_an_existing_active_job() {
    let store = LibraryStore::open_in_memory().unwrap();
    let queued = DurableDownloadSnapshot {
        job_id: "enqueue-race".into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    assert!(store.insert_download_snapshot_if_absent(&queued).unwrap());
    let mut active = queued.clone();
    active.state = DownloadState::Downloading;
    active.attempt = 1;
    store.save_download_snapshot(&active).unwrap();
    assert!(!store.insert_download_snapshot_if_absent(&queued).unwrap());
    assert_eq!(store.load_download_snapshots().unwrap(), vec![active]);
}

#[test]
fn stale_recovery_compare_and_swap_preserves_completed_job() {
    let store = LibraryStore::open_in_memory().unwrap();
    let running = DurableDownloadSnapshot {
        job_id: "recovery-race".into(),
        state: DownloadState::Downloading,
        bytes_downloaded: 42,
        total_bytes: Some(100),
        attempt: 1,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    store.save_download_snapshot(&running).unwrap();
    let mut completed = running.clone();
    completed.state = DownloadState::Completed;
    completed.bytes_downloaded = 100;
    store.save_download_snapshot(&completed).unwrap();

    let mut stale_repair = running.clone();
    stale_repair.state = DownloadState::RetryWait;
    stale_repair.retry_at_epoch_ms = Some(10_000);
    assert!(
        !store
            .replace_download_snapshot_if_unchanged(&running, &stale_repair)
            .unwrap()
    );
    assert_eq!(store.load_download_snapshots().unwrap(), vec![completed]);
}

#[test]
fn durable_download_snapshot_round_trips() {
    let store = LibraryStore::open_in_memory().unwrap();
    let snapshot = DurableDownloadSnapshot {
        job_id: "job-1".into(),
        state: DownloadState::RetryWait,
        bytes_downloaded: 44,
        total_bytes: Some(100),
        attempt: 2,
        retry_at_epoch_ms: Some(500),
        last_error: Some(CoreError::new(ErrorKind::NetworkTimeout, "timed out", true)),
    };
    store.save_download_snapshot(&snapshot).unwrap();
    assert_eq!(store.load_download_snapshots().unwrap(), vec![snapshot]);
}

#[test]
fn search_escapes_sql_like_wildcards() {
    let store = LibraryStore::open_in_memory().unwrap();
    let mut percent = item(true);
    percent.display_title = "100% Offline".into();
    store.promote_completed("job-1", &percent).unwrap();
    assert_eq!(store.list(Some("100%")).unwrap().len(), 1);
    assert!(store.list(Some("100_")).unwrap().is_empty());
}

#[test]
fn rejects_traversal_asset_paths() {
    let store = LibraryStore::open_in_memory().unwrap();
    let mut bad = item(true);
    bad.assets[0].relative_path = "../escape.mp4".into();
    assert_eq!(
        store.promote_completed("job-1", &bad).unwrap_err().kind,
        ErrorKind::InvalidInput
    );
}

#[test]
fn persisted_library_validator_checks_hash_not_just_length() {
    use sha2::Digest;

    let store = LibraryStore::open_in_memory().unwrap();
    let mut completed = item(true);
    completed.assets[0].bytes = 4;
    completed.assets[0].sha256 = Some(format!("{:x}", sha2::Sha256::digest(b"good")));
    store.promote_completed("job-1", &completed).unwrap();

    let root = tempdir().unwrap();
    let path = root.path().join("items/item-1/video.mp4");
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(&path, b"good").unwrap();
    store.validate_item_assets(root.path(), "item-1").unwrap();

    // Same size, different contents: the old metadata-only validator
    // reported Healthy even though the persisted SHA-256 no longer matched.
    std::fs::write(&path, b"evil").unwrap();
    assert_eq!(
        store
            .validate_item_assets(root.path(), "item-1")
            .unwrap_err()
            .kind,
        ErrorKind::CorruptAsset
    );
}

#[cfg(unix)]
#[test]
fn persisted_library_validator_rejects_symlinked_file_and_root() {
    use sha2::Digest;
    use std::os::unix::fs::symlink;

    let store = LibraryStore::open_in_memory().unwrap();
    let mut completed = item(true);
    completed.assets[0].bytes = 4;
    completed.assets[0].sha256 = Some(format!("{:x}", sha2::Sha256::digest(b"good")));
    store.promote_completed("job-1", &completed).unwrap();

    let root = tempdir().unwrap();
    let outside = tempdir().unwrap();
    let outside_file = outside.path().join("items/item-1/video.mp4");
    std::fs::create_dir_all(outside_file.parent().unwrap()).unwrap();
    std::fs::write(&outside_file, b"good").unwrap();
    let inside_file = root.path().join("items/item-1/video.mp4");
    std::fs::create_dir_all(inside_file.parent().unwrap()).unwrap();
    symlink(&outside_file, &inside_file).unwrap();
    assert_eq!(
        store
            .validate_item_assets(root.path(), "item-1")
            .unwrap_err()
            .kind,
        ErrorKind::CorruptAsset
    );

    let linked_root = root.path().join("linked-library");
    symlink(outside.path(), &linked_root).unwrap();
    assert_eq!(
        store
            .validate_item_assets(&linked_root, "item-1")
            .unwrap_err()
            .kind,
        ErrorKind::CorruptAsset
    );
    assert_eq!(std::fs::read(&outside_file).unwrap(), b"good");
}

#[test]
fn detects_missing_and_wrong_sized_assets() {
    let store = LibraryStore::open_in_memory().unwrap();
    store.promote_completed("job-1", &item(true)).unwrap();
    let root = tempdir().unwrap();
    assert_eq!(
        store
            .validate_item_assets(root.path(), "item-1")
            .unwrap_err()
            .kind,
        ErrorKind::MissingAsset
    );
    let path = root.path().join("items/item-1");
    std::fs::create_dir_all(&path).unwrap();
    std::fs::write(path.join("video.mp4"), [0_u8; 3]).unwrap();
    assert_eq!(
        store
            .validate_item_assets(root.path(), "item-1")
            .unwrap_err()
            .kind,
        ErrorKind::CorruptAsset
    );
}
