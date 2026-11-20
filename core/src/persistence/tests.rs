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

#[test]
fn bundled_sqlite_file_reopen_preserves_durable_queue_and_foreign_keys() {
    let dir = tempdir().unwrap();
    let path = dir.path().join("bundle-compat.sqlite3");
    let queued = DurableDownloadSnapshot {
        job_id: "reopen-job".into(),
        state: DownloadState::RetryWait,
        bytes_downloaded: 91,
        total_bytes: Some(100),
        attempt: 2,
        retry_at_epoch_ms: Some(10_000),
        last_error: None,
    };
    {
        let store = LibraryStore::open(&path).unwrap();
        assert_eq!(store.schema_version().unwrap(), SCHEMA_VERSION);
        store.save_download_snapshot(&queued).unwrap();
        store.stage_asset("job-1", &item(true).assets[0]).unwrap();
        store.promote_completed("job-1", &item(true)).unwrap();
    }
    let reopened = LibraryStore::open(&path).unwrap();
    assert_eq!(reopened.schema_version().unwrap(), SCHEMA_VERSION);
    assert_eq!(reopened.load_download_snapshots().unwrap(), vec![queued]);
    assert_eq!(reopened.get("item-1").unwrap(), Some(item(true)));
    let guard = reopened.connection().unwrap();
    let fk: i64 = guard
        .query_row("PRAGMA foreign_keys", [], |row| row.get(0))
        .unwrap();
    assert_eq!(
        fk, 1,
        "every opened SQLite connection must enforce foreign keys"
    );
    assert!(
        guard
            .execute(
                r#"INSERT INTO assets(item_id,asset_id,kind_json,relative_path,bytes)
                   VALUES('missing-parent','orphan','"Video"','items/orphan',1)"#,
                [],
            )
            .is_err(),
        "an orphan media asset must be rejected by the foreign key"
    );
}

#[test]
fn failed_asset_insert_rolls_back_promotion_and_preserves_staging_after_reopen() {
    let dir = tempdir().unwrap();
    let path = dir.path().join("rollback.sqlite3");
    let store = LibraryStore::open(&path).unwrap();
    let complete = item(true);
    store.stage_asset("job-1", &complete.assets[0]).unwrap();
    {
        let connection = store.connection().unwrap();
        connection
            .execute_batch(
                "CREATE TRIGGER injected_asset_failure BEFORE INSERT ON assets
                 BEGIN SELECT RAISE(ABORT, 'injected SQLite asset error'); END;",
            )
            .unwrap();
    }

    assert_eq!(
        store
            .promote_completed("job-1", &complete)
            .unwrap_err()
            .kind,
        ErrorKind::Persistence
    );
    assert!(store.list(None).unwrap().is_empty());
    assert_eq!(store.staged_job_ids().unwrap(), vec!["job-1"]);

    drop(store);
    let connection = rusqlite::Connection::open(&path).unwrap();
    connection
        .execute_batch("DROP TRIGGER injected_asset_failure")
        .unwrap();
    drop(connection);
    let reopened = LibraryStore::open(&path).unwrap();
    assert!(reopened.list(None).unwrap().is_empty());
    assert_eq!(reopened.staged_job_ids().unwrap(), vec!["job-1"]);
    reopened.promote_completed("job-1", &complete).unwrap();
    assert_eq!(reopened.list(None).unwrap(), vec![complete]);
    assert!(reopened.staged_job_ids().unwrap().is_empty());
}

#[test]
fn future_schema_and_corrupt_file_fail_closed_without_destroying_user_data() {
    let dir = tempdir().unwrap();
    let future_path = dir.path().join("future.sqlite3");
    let conn = rusqlite::Connection::open(&future_path).unwrap();
    conn.execute_batch(
        "CREATE TABLE schema_meta (id INTEGER PRIMARY KEY, version INTEGER NOT NULL);
         INSERT INTO schema_meta(id,version) VALUES(1,999);",
    )
    .unwrap();
    drop(conn);
    assert_eq!(
        LibraryStore::open(&future_path).unwrap_err().kind,
        ErrorKind::Persistence
    );
    let conn = rusqlite::Connection::open(&future_path).unwrap();
    let version: i64 = conn
        .query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| {
            row.get(0)
        })
        .unwrap();
    assert_eq!(
        version, 999,
        "migration may not downgrade an unknown future schema"
    );
    let table_count: i64 = conn
        .query_row(
            "SELECT COUNT(*) FROM sqlite_master WHERE name='library_items'",
            [],
            |row| row.get(0),
        )
        .unwrap();
    assert_eq!(
        table_count, 0,
        "failed migration must not create partial tables"
    );
    drop(conn);

    let damaged = dir.path().join("corrupt.sqlite3");
    let original = b"not a valid SQLite database file";
    std::fs::write(&damaged, original).unwrap();
    assert_eq!(
        LibraryStore::open(&damaged).unwrap_err().kind,
        ErrorKind::Persistence
    );
    assert_eq!(std::fs::read(&damaged).unwrap(), original);
}

#[test]
fn independent_sqlite_worker_connections_race_for_one_authoritative_claim() {
    use std::sync::{Arc, Barrier};

    let dir = tempdir().unwrap();
    let path = dir.path().join("worker-claim-race.sqlite3");
    let owner = LibraryStore::open(&path).unwrap();
    let queued = DurableDownloadSnapshot {
        job_id: "shared-race".into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: Some(100),
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    owner.save_download_snapshot(&queued).unwrap();
    drop(owner);

    let rendezvous = Arc::new(Barrier::new(3));
    let mut handles = Vec::new();
    for worker in 1..=2_u64 {
        let database = path.clone();
        let gate = Arc::clone(&rendezvous);
        handles.push(std::thread::spawn(move || {
            let store = LibraryStore::open(&database).unwrap();
            let previous = store.load_download_snapshots().unwrap().remove(0);
            let mut claim = previous.clone();
            claim.state = DownloadState::Resolving;
            claim.bytes_downloaded = worker;
            claim.attempt = 1;
            gate.wait();
            store
                .replace_download_snapshot_if_unchanged(&previous, &claim)
                .unwrap()
        }));
    }
    rendezvous.wait();
    let successes = handles
        .into_iter()
        .map(|worker| worker.join().unwrap())
        .filter(|won| *won)
        .count();
    assert_eq!(successes, 1, "only one SQLite connection may claim the job");
    let observed = LibraryStore::open(&path)
        .unwrap()
        .load_download_snapshots()
        .unwrap();
    assert_eq!(observed.len(), 1);
    assert_eq!(observed[0].state, DownloadState::Resolving);
    assert_eq!(observed[0].attempt, 1);
    assert!((1..=2).contains(&observed[0].bytes_downloaded));
}

#[test]
fn independent_sqlite_completion_and_cancel_race_never_promotes_canceled_item() {
    use std::sync::{Arc, Barrier};

    let dir = tempdir().unwrap();
    let path = dir.path().join("completion-cancel-race.sqlite3");
    let owner = LibraryStore::open(&path).unwrap();
    let complete = item(true);
    let previous = DurableDownloadSnapshot {
        job_id: complete.item_id.clone(),
        state: DownloadState::Verifying,
        bytes_downloaded: 100,
        total_bytes: Some(100),
        attempt: 1,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    owner.save_download_snapshot(&previous).unwrap();
    owner
        .stage_asset(&previous.job_id, &complete.assets[0])
        .unwrap();
    drop(owner);

    let rendezvous = Arc::new(Barrier::new(3));
    let completion_gate = Arc::clone(&rendezvous);
    let cancel_gate = Arc::clone(&rendezvous);
    let completion_path = path.clone();
    let cancel_path = path.clone();
    let completed_from = previous.clone();
    let canceled_from = previous.clone();

    let complete_handle = std::thread::spawn(move || {
        let store = LibraryStore::open(&completion_path).unwrap();
        let mut completed = completed_from.clone();
        completed.state = DownloadState::Completed;
        completion_gate.wait();
        store
            .promote_completed_if_unchanged(
                &completed_from.job_id,
                &complete,
                &completed_from,
                &completed,
            )
            .unwrap()
    });
    let cancel_handle = std::thread::spawn(move || {
        let store = LibraryStore::open(&cancel_path).unwrap();
        let mut canceled = canceled_from.clone();
        canceled.state = DownloadState::Canceled;
        cancel_gate.wait();
        store
            .replace_download_snapshot_if_unchanged(&canceled_from, &canceled)
            .unwrap()
    });
    rendezvous.wait();
    let completed = complete_handle.join().unwrap();
    let canceled = cancel_handle.join().unwrap();
    assert_ne!(completed, canceled, "exactly one terminal transition wins");

    let after = LibraryStore::open(&path).unwrap();
    let stored = after.load_download_snapshots().unwrap();
    assert_eq!(stored.len(), 1);
    if completed {
        assert_eq!(stored[0].state, DownloadState::Completed);
        assert_eq!(after.list(None).unwrap(), vec![item(true)]);
        assert!(after.staged_job_ids().unwrap().is_empty());
    } else {
        assert_eq!(stored[0].state, DownloadState::Canceled);
        assert!(after.list(None).unwrap().is_empty());
        assert_eq!(after.staged_job_ids().unwrap(), vec!["item-1"]);
    }
}

#[test]
fn failed_initial_migration_rolls_back_ddl_and_preserves_user_tables_after_reopen() {
    let dir = tempdir().unwrap();
    let path = dir.path().join("failed-migration.sqlite3");
    // Simulate a legacy v0 file whose assets table name conflicts with the
    // new migration, after library_items and its unique index have been created
    // in the same transaction. The original sentinel must survive.
    {
        let connection = rusqlite::Connection::open(&path).unwrap();
        connection
            .execute_batch(
                "CREATE TABLE schema_meta (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    version INTEGER NOT NULL
                 );
                 INSERT INTO schema_meta(id, version) VALUES(1, 0);
                 CREATE TABLE assets(marker TEXT NOT NULL);
                 INSERT INTO assets(marker) VALUES('USER_DATA_MUST_SURVIVE');",
            )
            .unwrap();
    }

    for attempt in 0..2 {
        let failure = LibraryStore::open(&path).expect_err("migration must fail closed");
        assert_eq!(failure.kind, ErrorKind::Persistence, "attempt {attempt}");
        let connection = rusqlite::Connection::open(&path).unwrap();
        let version: i64 = connection
            .query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(version, 0, "attempt {attempt}: no premature schema bump");
        let marker: String = connection
            .query_row("SELECT marker FROM assets", [], |row| row.get(0))
            .unwrap();
        assert_eq!(marker, "USER_DATA_MUST_SURVIVE");
        for table in ["library_items", "staged_assets", "download_jobs"] {
            let created: i64 = connection
                .query_row(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?1",
                    [table],
                    |row| row.get(0),
                )
                .unwrap();
            assert_eq!(created, 0, "attempt {attempt}: partial table {table}");
        }
        let created_index: i64 = connection
            .query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='library_source_identity'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(created_index, 0, "attempt {attempt}: partial unique index");
    }
}

#[test]
fn corrupt_download_snapshot_is_not_silently_deleted_during_reopen() {
    let dir = tempdir().unwrap();
    let path = dir.path().join("bad-snapshot.sqlite3");
    let valid = DurableDownloadSnapshot {
        job_id: "valid-job".into(),
        state: DownloadState::Queued,
        bytes_downloaded: 1,
        total_bytes: Some(100),
        attempt: 1,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    {
        let store = LibraryStore::open(&path).unwrap();
        store.save_download_snapshot(&valid).unwrap();
        let connection = store.connection().unwrap();
        connection
            .execute(
                "INSERT INTO download_jobs(job_id, state_json, bytes_downloaded, attempt)
                 VALUES('invalid-state', 'not-json', 10, 0)",
                [],
            )
            .unwrap();
    }

    for attempt in 0..2 {
        let store = LibraryStore::open(&path).expect("schema remains valid on reopen");
        assert_eq!(store.schema_version().unwrap(), SCHEMA_VERSION);
        let error = store
            .load_download_snapshots()
            .expect_err("malformed state must not be treated as an empty queue");
        assert_eq!(error.kind, ErrorKind::Persistence, "attempt {attempt}");
        // Read raw bytes instead of the typed projection: a damaged job must
        // remain for explicit repair, not be silently discarded on startup.
        let connection = store.connection().unwrap();
        let damaged: String = connection
            .query_row(
                "SELECT state_json FROM download_jobs WHERE job_id='invalid-state'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(damaged, "not-json");
        let valid_state: String = connection
            .query_row(
                "SELECT state_json FROM download_jobs WHERE job_id='valid-job'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(
            valid_state,
            serde_json::to_string(&DownloadState::Queued).unwrap()
        );
    }
}

#[test]
fn injected_queue_claim_stage_cancel_and_completion_write_faults_recover_after_reopen() {
    let dir = tempdir().unwrap();
    for phase in ["enqueue", "claim", "stage", "cancel", "completion"] {
        let path = dir.path().join(format!("phase-{phase}.sqlite3"));
        let store = LibraryStore::open(&path).unwrap();
        let mut previous = DurableDownloadSnapshot {
            job_id: "item-1".into(),
            state: DownloadState::Queued,
            bytes_downloaded: 0,
            total_bytes: Some(100),
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: None,
        };
        if phase == "cancel" {
            previous.state = DownloadState::Downloading;
            previous.attempt = 1;
        }
        if phase == "completion" {
            previous.state = DownloadState::Verifying;
            previous.bytes_downloaded = 100;
            previous.attempt = 1;
        }
        if phase != "enqueue" {
            store.save_download_snapshot(&previous).unwrap();
        }
        if phase == "completion" {
            store.stage_asset("item-1", &item(true).assets[0]).unwrap();
        }

        let (operation, table) = match phase {
            "enqueue" => ("INSERT", "download_jobs"),
            "claim" | "cancel" | "completion" => ("UPDATE", "download_jobs"),
            "stage" => ("INSERT", "staged_assets"),
            _ => unreachable!(),
        };
        store
            .connection()
            .unwrap()
            .execute_batch(&format!(
                "CREATE TRIGGER inject_phase_failure BEFORE {operation} ON {table}
                 BEGIN SELECT RAISE(ABORT, 'test-only injected SQLite failure'); END;"
            ))
            .unwrap();

        let mut next = previous.clone();
        match phase {
            "claim" => {
                next.state = DownloadState::Resolving;
                next.attempt = 1;
            }
            "cancel" => next.state = DownloadState::Canceled,
            "completion" => next.state = DownloadState::Completed,
            _ => {}
        }
        let attempt: Result<(), CoreError> = match phase {
            "enqueue" => store
                .insert_download_snapshot_if_absent(&previous)
                .map(|_| ()),
            "claim" | "cancel" => store
                .replace_download_snapshot_if_unchanged(&previous, &next)
                .map(|_| ()),
            "stage" => store.stage_asset("item-1", &item(true).assets[0]),
            "completion" => store
                .promote_completed_if_unchanged("item-1", &item(true), &previous, &next)
                .map(|_| ()),
            _ => unreachable!(),
        };
        assert_eq!(
            attempt.expect_err("injected write must fail").kind,
            ErrorKind::Persistence,
            "{phase}"
        );
        drop(store);

        // A failure at any write phase must not change a durable job or
        // remove a staged asset. Each fresh connection must see the same data.
        let reopened = LibraryStore::open(&path).unwrap();
        let expected_previous = if phase == "enqueue" {
            Vec::new()
        } else {
            vec![previous.clone()]
        };
        assert_eq!(
            reopened.load_download_snapshots().unwrap(),
            expected_previous,
            "phase {phase}: durable snapshot must not be partially written"
        );
        assert_eq!(
            reopened.staged_job_ids().unwrap(),
            if phase == "completion" {
                vec!["item-1".to_owned()]
            } else {
                Vec::new()
            },
            "phase {phase}: staging must remain consistent"
        );
        assert!(
            reopened.list(None).unwrap().is_empty(),
            "phase {phase}: no library item may be promoted after a failed write"
        );
        reopened
            .connection()
            .unwrap()
            .execute_batch("DROP TRIGGER inject_phase_failure")
            .unwrap();

        // Removing the test-only trigger permits the exact intended operation
        // to finish, with no duplicate job and no partial metadata promotion.
        match phase {
            "enqueue" => {
                assert!(
                    reopened
                        .insert_download_snapshot_if_absent(&previous)
                        .unwrap()
                );
                assert_eq!(reopened.load_download_snapshots().unwrap(), vec![previous]);
            }
            "claim" | "cancel" => {
                assert!(
                    reopened
                        .replace_download_snapshot_if_unchanged(&previous, &next)
                        .unwrap(),
                    "phase {phase}: retry with original snapshot must win"
                );
                assert_eq!(reopened.load_download_snapshots().unwrap(), vec![next]);
            }
            "stage" => {
                reopened
                    .stage_asset("item-1", &item(true).assets[0])
                    .unwrap();
                assert_eq!(
                    reopened.staged_job_ids().unwrap(),
                    vec!["item-1".to_owned()]
                );
                assert_eq!(reopened.load_download_snapshots().unwrap(), vec![previous]);
            }
            "completion" => {
                assert!(
                    reopened
                        .promote_completed_if_unchanged("item-1", &item(true), &previous, &next,)
                        .unwrap()
                );
                assert_eq!(reopened.load_download_snapshots().unwrap(), vec![next]);
                assert_eq!(reopened.list(None).unwrap(), vec![item(true)]);
                assert!(reopened.staged_job_ids().unwrap().is_empty());
            }
            _ => unreachable!(),
        }
    }
}

#[test]
fn independent_sqlite_pause_and_retry_race_preserves_one_authoritative_state() {
    use std::sync::{Arc, Barrier};

    let dir = tempdir().unwrap();
    let path = dir.path().join("pause-retry-race.sqlite3");
    let owner = LibraryStore::open(&path).unwrap();
    let waiting = DurableDownloadSnapshot {
        job_id: "pause-retry".into(),
        state: DownloadState::RetryWait,
        bytes_downloaded: 45,
        total_bytes: Some(100),
        attempt: 2,
        retry_at_epoch_ms: Some(60_000),
        last_error: None,
    };
    owner.save_download_snapshot(&waiting).unwrap();
    drop(owner);
    let gate = Arc::new(Barrier::new(3));
    let mut workers = Vec::new();
    for pause in [true, false] {
        let database = path.clone();
        let rendezvous = Arc::clone(&gate);
        workers.push(std::thread::spawn(move || {
            let store = LibraryStore::open(&database).unwrap();
            let previous = store.load_download_snapshots().unwrap().remove(0);
            let mut next = previous.clone();
            next.state = if pause {
                DownloadState::Paused
            } else {
                DownloadState::Queued
            };
            next.retry_at_epoch_ms = None;
            if !pause {
                next.attempt += 1;
            }
            rendezvous.wait();
            let won = store
                .replace_download_snapshot_if_unchanged(&previous, &next)
                .unwrap();
            (won, next)
        }));
    }
    gate.wait();
    let results = workers
        .into_iter()
        .map(|thread| thread.join().unwrap())
        .collect::<Vec<_>>();
    assert_eq!(
        results.iter().filter(|(won, _)| *won).count(),
        1,
        "only pause OR retry may win the same previously observed snapshot"
    );
    let expected = results
        .into_iter()
        .find_map(|(won, next)| won.then_some(next))
        .unwrap();
    let reopened = LibraryStore::open(&path).unwrap();
    assert_eq!(reopened.load_download_snapshots().unwrap(), vec![expected]);
    assert!(reopened.list(None).unwrap().is_empty());
}

#[test]
fn concurrent_duplicate_schedule_insert_writes_only_one_durable_job() {
    use std::sync::{Arc, Barrier};

    let dir = tempdir().unwrap();
    let path = dir.path().join("duplicate-enqueue.sqlite3");
    let owner = LibraryStore::open(&path).unwrap();
    drop(owner);
    let queued = DurableDownloadSnapshot {
        job_id: "duplicate-schedule".into(),
        state: DownloadState::Queued,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    };
    let gate = Arc::new(Barrier::new(4));
    let mut workers = Vec::new();
    for _ in 0..3 {
        let database = path.clone();
        let rendezvous = Arc::clone(&gate);
        let request = queued.clone();
        workers.push(std::thread::spawn(move || {
            let store = LibraryStore::open(&database).unwrap();
            rendezvous.wait();
            store.insert_download_snapshot_if_absent(&request).unwrap()
        }));
    }
    gate.wait();
    assert_eq!(
        workers
            .into_iter()
            .map(|worker| worker.join().unwrap())
            .filter(|inserted| *inserted)
            .count(),
        1,
        "concurrent scheduler requests must create exactly one queued job"
    );
    let reopened = LibraryStore::open(&path).unwrap();
    assert_eq!(reopened.load_download_snapshots().unwrap(), vec![queued]);
}
