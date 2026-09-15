use offline_yt_core::{LibraryItem, LibraryStore, LocalAsset, MediaKind, SourceIdentity};
use rusqlite::Connection;
use tempfile::tempdir;

fn item() -> LibraryItem {
    LibraryItem {
        item_id: "item-restart".into(),
        source: SourceIdentity {
            provider: "fixture".into(),
            media_id: "source-restart".into(),
            canonical_url: Some("https://fixture.invalid/restart".into()),
        },
        display_title: "Restart fixture".into(),
        duration_ms: Some(30_000),
        quality_label: "720p".into(),
        assets: vec![LocalAsset {
            asset_id: "combined".into(),
            kind: MediaKind::Video,
            relative_path: "items/item-restart/video.mp4".into(),
            bytes: 4,
            sha256: None,
            mime_type: Some("video/mp4".into()),
        }],
        created_at_epoch_ms: 1,
        playback_position_ms: 0,
        completed: true,
    }
}

#[test]
fn staged_assets_survive_restart_and_promote_atomically() {
    let root = tempdir().unwrap();
    let db = root.path().join("library.sqlite3");
    let complete = item();

    {
        let store = LibraryStore::open(&db).unwrap();
        store
            .stage_asset("job-restart", &complete.assets[0])
            .unwrap();
        assert_eq!(store.staged_job_ids().unwrap(), vec!["job-restart"]);
    }

    let reopened = LibraryStore::open(&db).unwrap();
    assert_eq!(reopened.staged_job_ids().unwrap(), vec!["job-restart"]);
    reopened
        .promote_completed("job-restart", &complete)
        .unwrap();
    assert!(reopened.staged_job_ids().unwrap().is_empty());
    assert_eq!(reopened.get("item-restart").unwrap(), Some(complete));
}

#[test]
fn failed_incomplete_promotion_leaves_staging_recoverable() {
    let root = tempdir().unwrap();
    let db = root.path().join("library.sqlite3");
    let mut incomplete = item();
    incomplete.completed = false;

    {
        let store = LibraryStore::open(&db).unwrap();
        store
            .stage_asset("job-restart", &incomplete.assets[0])
            .unwrap();
        assert!(
            store
                .promote_completed("job-restart", &incomplete)
                .is_err()
        );
    }

    let reopened = LibraryStore::open(&db).unwrap();
    assert_eq!(reopened.staged_job_ids().unwrap(), vec!["job-restart"]);
    assert!(reopened.get("item-restart").unwrap().is_none());
}

#[test]
fn newer_database_version_is_rejected_without_mutation() {
    let root = tempdir().unwrap();
    let db = root.path().join("future.sqlite3");
    let connection = Connection::open(&db).unwrap();
    connection
        .execute_batch(
            "CREATE TABLE schema_meta (id INTEGER PRIMARY KEY CHECK (id = 1), version INTEGER NOT NULL);\n             INSERT INTO schema_meta(id, version) VALUES(1, 999);",
        )
        .unwrap();
    drop(connection);

    assert!(LibraryStore::open(&db).is_err());
    let connection = Connection::open(&db).unwrap();
    let version: i64 = connection
        .query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| {
            row.get(0)
        })
        .unwrap();
    assert_eq!(version, 999);
}
