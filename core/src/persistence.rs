use crate::domain::{CoreError, ErrorKind, LibraryItem, LocalAsset, SourceIdentity};
use crate::events::DurableDownloadSnapshot;
use crate::state::DownloadState;
use rusqlite::{Connection, OptionalExtension, Transaction, params};
use std::path::{Component, Path};
use std::sync::{Arc, Mutex, MutexGuard};

pub const SCHEMA_VERSION: i64 = 1;

#[derive(Debug, Clone)]
pub struct LibraryStore {
    connection: Arc<Mutex<Connection>>,
}

impl LibraryStore {
    pub fn open(path: impl AsRef<Path>) -> Result<Self, CoreError> {
        let connection = Connection::open(path).map_err(db_error)?;
        let store = Self { connection: Arc::new(Mutex::new(connection)) };
        store.migrate()?;
        Ok(store)
    }

    pub fn open_in_memory() -> Result<Self, CoreError> {
        let connection = Connection::open_in_memory().map_err(db_error)?;
        let store = Self { connection: Arc::new(Mutex::new(connection)) };
        store.migrate()?;
        Ok(store)
    }

    fn connection(&self) -> Result<MutexGuard<'_, Connection>, CoreError> {
        self.connection.lock().map_err(|_| CoreError::new(ErrorKind::Persistence, "library database lock poisoned", false))
    }

    pub fn migrate(&self) -> Result<(), CoreError> {
        let connection = self.connection()?;
        connection.execute_batch("PRAGMA foreign_keys = ON;
                 CREATE TABLE IF NOT EXISTS schema_meta (id INTEGER PRIMARY KEY CHECK (id = 1), version INTEGER NOT NULL);
                 INSERT OR IGNORE INTO schema_meta(id, version) VALUES(1, 0);").map_err(db_error)?;
        let version: i64 = connection.query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| row.get(0)).map_err(db_error)?;
        if version > SCHEMA_VERSION { return Err(CoreError::new(ErrorKind::Persistence, "library database is newer than this application", false)); }
        if version < 1 {
            connection.execute_batch("BEGIN IMMEDIATE;
                     CREATE TABLE library_items (
                       item_id TEXT PRIMARY KEY, provider TEXT NOT NULL, source_media_id TEXT NOT NULL,
                       canonical_url TEXT, display_title TEXT NOT NULL, duration_ms INTEGER,
                       quality_label TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL,
                       playback_position_ms INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0);
                     CREATE UNIQUE INDEX library_source_identity ON library_items(provider, source_media_id);
                     CREATE TABLE assets (
                       item_id TEXT NOT NULL REFERENCES library_items(item_id) ON DELETE CASCADE,
                       asset_id TEXT NOT NULL, kind_json TEXT NOT NULL, relative_path TEXT NOT NULL,
                       bytes INTEGER NOT NULL, sha256 TEXT, mime_type TEXT, PRIMARY KEY(item_id, asset_id));
                     CREATE TABLE staged_assets (
                       job_id TEXT NOT NULL, asset_id TEXT NOT NULL, relative_path TEXT NOT NULL,
                       bytes INTEGER NOT NULL, sha256 TEXT, PRIMARY KEY(job_id, asset_id));
                     CREATE TABLE download_jobs (
                       job_id TEXT PRIMARY KEY, state_json TEXT NOT NULL, bytes_downloaded INTEGER NOT NULL,
                       total_bytes INTEGER, attempt INTEGER NOT NULL, retry_at_epoch_ms INTEGER, error_json TEXT);
                     UPDATE schema_meta SET version=1 WHERE id=1; COMMIT;").map_err(db_error)?;
        }
        Ok(())
    }

    pub fn schema_version(&self) -> Result<i64, CoreError> {
        self.connection()?.query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| row.get(0)).map_err(db_error)
    }

    pub fn stage_asset(&self, job_id: &str, asset: &LocalAsset) -> Result<(), CoreError> {
        validate_relative_asset_path(&asset.relative_path)?;
        self.connection()?.execute("INSERT INTO staged_assets(job_id, asset_id, relative_path, bytes, sha256)
                 VALUES(?1, ?2, ?3, ?4, ?5) ON CONFLICT(job_id, asset_id) DO UPDATE SET
                 relative_path=excluded.relative_path, bytes=excluded.bytes, sha256=excluded.sha256",
            params![job_id, asset.asset_id, asset.relative_path, to_i64(asset.bytes)?, asset.sha256]).map_err(db_error)?;
        Ok(())
    }

    pub fn promote_completed(&self, job_id: &str, item: &LibraryItem) -> Result<(), CoreError> {
        if !item.completed { return Err(CoreError::new(ErrorKind::IntegrityFailure, "cannot promote an incomplete library item", false)); }
        for asset in &item.assets { validate_relative_asset_path(&asset.relative_path)?; }
        let mut connection = self.connection()?;
        let transaction = connection.transaction().map_err(db_error)?;
        upsert_item(&transaction, item)?;
        transaction.execute("DELETE FROM assets WHERE item_id=?1", [item.item_id.as_str()]).map_err(db_error)?;
        for asset in &item.assets { insert_asset(&transaction, &item.item_id, asset)?; }
        transaction.execute("DELETE FROM staged_assets WHERE job_id=?1", [job_id]).map_err(db_error)?;
        transaction.commit().map_err(db_error)
    }

    pub fn list(&self, query: Option<&str>) -> Result<Vec<LibraryItem>, CoreError> {
        let connection = self.connection()?;
        let like = query.map(|value| format!("%{}%", escape_like(value)));
        let mut statement = connection.prepare("SELECT item_id, provider, source_media_id, canonical_url, display_title,
                        duration_ms, quality_label, created_at_epoch_ms, playback_position_ms, completed
                 FROM library_items WHERE (?1 IS NULL OR display_title LIKE ?1 ESCAPE '\\')
                 ORDER BY created_at_epoch_ms DESC").map_err(db_error)?;
        let rows = statement.query_map([like.as_deref()], map_item_row).map_err(db_error)?;
        let mut items = Vec::new();
        for row in rows { let mut item = row.map_err(db_error)?; item.assets = load_assets(&connection, &item.item_id)?; items.push(item); }
        Ok(items)
    }

    pub fn get(&self, item_id: &str) -> Result<Option<LibraryItem>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection.prepare("SELECT item_id, provider, source_media_id, canonical_url, display_title,
                        duration_ms, quality_label, created_at_epoch_ms, playback_position_ms, completed
                 FROM library_items WHERE item_id=?1").map_err(db_error)?;
        let mut item = statement.query_row([item_id], map_item_row).optional().map_err(db_error)?;
        if let Some(found) = &mut item { found.assets = load_assets(&connection, item_id)?; }
        Ok(item)
    }

    pub fn rename(&self, item_id: &str, title: &str) -> Result<bool, CoreError> {
        let changed = self.connection()?.execute("UPDATE library_items SET display_title=?2 WHERE item_id=?1", params![item_id, title]).map_err(db_error)?;
        Ok(changed == 1)
    }

    pub fn save_playback_position(&self, item_id: &str, position_ms: u64) -> Result<bool, CoreError> {
        let changed = self.connection()?.execute("UPDATE library_items SET playback_position_ms=?2 WHERE item_id=?1", params![item_id, to_i64(position_ms)?]).map_err(db_error)?;
        Ok(changed == 1)
    }

    pub fn delete(&self, item_id: &str) -> Result<Option<LibraryItem>, CoreError> {
        let item = self.get(item_id)?;
        if item.is_some() { self.connection()?.execute("DELETE FROM library_items WHERE item_id=?1", [item_id]).map_err(db_error)?; }
        Ok(item)
    }

    pub fn validate_item_assets(&self, library_root: &Path, item_id: &str) -> Result<(), CoreError> {
        let item = self.get(item_id)?.ok_or_else(|| CoreError::new(ErrorKind::MissingAsset, "library item does not exist", false))?;
        for asset in item.assets {
            validate_relative_asset_path(&asset.relative_path)?;
            let path = library_root.join(&asset.relative_path);
            let metadata = std::fs::metadata(&path).map_err(|_| CoreError::new(ErrorKind::MissingAsset, format!("missing local asset {}", asset.asset_id), false))?;
            if !metadata.is_file() || metadata.len() != asset.bytes {
                return Err(CoreError::new(ErrorKind::CorruptAsset, format!("local asset {} has unexpected size", asset.asset_id), false));
            }
        }
        Ok(())
    }

    pub fn save_download_snapshot(&self, snapshot: &DurableDownloadSnapshot) -> Result<(), CoreError> {
        let state = serde_json::to_string(&snapshot.state).map_err(json_error)?;
        let error = snapshot.last_error.as_ref().map(serde_json::to_string).transpose().map_err(json_error)?;
        self.connection()?.execute("INSERT INTO download_jobs(job_id, state_json, bytes_downloaded, total_bytes, attempt, retry_at_epoch_ms, error_json)
                 VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7) ON CONFLICT(job_id) DO UPDATE SET
                 state_json=excluded.state_json, bytes_downloaded=excluded.bytes_downloaded, total_bytes=excluded.total_bytes,
                 attempt=excluded.attempt, retry_at_epoch_ms=excluded.retry_at_epoch_ms, error_json=excluded.error_json",
            params![snapshot.job_id, state, to_i64(snapshot.bytes_downloaded)?, snapshot.total_bytes.map(to_i64).transpose()?, i64::from(snapshot.attempt), snapshot.retry_at_epoch_ms.map(to_i64).transpose()?, error]).map_err(db_error)?;
        Ok(())
    }

    pub fn load_download_snapshots(&self) -> Result<Vec<DurableDownloadSnapshot>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection.prepare("SELECT job_id, state_json, bytes_downloaded, total_bytes, attempt, retry_at_epoch_ms, error_json FROM download_jobs ORDER BY job_id").map_err(db_error)?;
        let rows = statement.query_map([], |row| {
            let state_json: String = row.get(1)?; let error_json: Option<String> = row.get(6)?;
            let state = serde_json::from_str::<DownloadState>(&state_json).map_err(|error| rusqlite::Error::FromSqlConversionFailure(1, rusqlite::types::Type::Text, Box::new(error)))?;
            let last_error = error_json.map(|value| serde_json::from_str(&value)).transpose().map_err(|error| rusqlite::Error::FromSqlConversionFailure(6, rusqlite::types::Type::Text, Box::new(error)))?;
            Ok(DurableDownloadSnapshot { job_id: row.get(0)?, state, bytes_downloaded: from_i64(row.get(2)?)?, total_bytes: row.get::<_, Option<i64>>(3)?.map(from_i64).transpose()?, attempt: u32::try_from(row.get::<_, i64>(4)?).map_err(|error| rusqlite::Error::FromSqlConversionFailure(4, rusqlite::types::Type::Integer, Box::new(error)))?, retry_at_epoch_ms: row.get::<_, Option<i64>>(5)?.map(from_i64).transpose()?, last_error })
        }).map_err(db_error)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
    }

    pub fn staged_job_ids(&self) -> Result<Vec<String>, CoreError> {
        let connection = self.connection()?; let mut statement = connection.prepare("SELECT DISTINCT job_id FROM staged_assets ORDER BY job_id").map_err(db_error)?;
        let rows = statement.query_map([], |row| row.get(0)).map_err(db_error)?; rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
    }
}

fn escape_like(value: &str) -> String { value.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_") }

fn validate_relative_asset_path(value: &str) -> Result<(), CoreError> {
    let path = Path::new(value);
    if value.is_empty() || path.is_absolute() || path.components().any(|component| matches!(component, Component::ParentDir | Component::RootDir | Component::Prefix(_))) {
        return Err(CoreError::new(ErrorKind::InvalidInput, "asset path must be a safe relative library path", false));
    }
    Ok(())
}

fn upsert_item(transaction: &Transaction<'_>, item: &LibraryItem) -> Result<(), CoreError> {
    transaction.execute("INSERT INTO library_items(item_id, provider, source_media_id, canonical_url, display_title, duration_ms, quality_label, created_at_epoch_ms, playback_position_ms, completed)
             VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10) ON CONFLICT(item_id) DO UPDATE SET
               provider=excluded.provider, source_media_id=excluded.source_media_id, canonical_url=excluded.canonical_url,
               display_title=excluded.display_title, duration_ms=excluded.duration_ms, quality_label=excluded.quality_label,
               created_at_epoch_ms=excluded.created_at_epoch_ms, playback_position_ms=excluded.playback_position_ms, completed=excluded.completed",
        params![item.item_id, item.source.provider, item.source.media_id, item.source.canonical_url, item.display_title, item.duration_ms.map(to_i64).transpose()?, item.quality_label, to_i64(item.created_at_epoch_ms)?, to_i64(item.playback_position_ms)?, i64::from(item.completed)]).map_err(db_error)?; Ok(())
}

fn insert_asset(transaction: &Transaction<'_>, item_id: &str, asset: &LocalAsset) -> Result<(), CoreError> {
    transaction.execute("INSERT INTO assets(item_id, asset_id, kind_json, relative_path, bytes, sha256, mime_type) VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![item_id, asset.asset_id, serde_json::to_string(&asset.kind).map_err(json_error)?, asset.relative_path, to_i64(asset.bytes)?, asset.sha256, asset.mime_type]).map_err(db_error)?; Ok(())
}

fn map_item_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<LibraryItem> {
    Ok(LibraryItem { item_id: row.get(0)?, source: SourceIdentity { provider: row.get(1)?, media_id: row.get(2)?, canonical_url: row.get(3)? }, display_title: row.get(4)?, duration_ms: row.get::<_, Option<i64>>(5)?.map(from_i64).transpose()?, quality_label: row.get(6)?, assets: Vec::new(), created_at_epoch_ms: from_i64(row.get(7)?)?, playback_position_ms: from_i64(row.get(8)?)?, completed: row.get::<_, i64>(9)? != 0 })
}

fn load_assets(connection: &Connection, item_id: &str) -> Result<Vec<LocalAsset>, CoreError> {
    let mut statement = connection.prepare("SELECT asset_id, kind_json, relative_path, bytes, sha256, mime_type FROM assets WHERE item_id=?1 ORDER BY asset_id").map_err(db_error)?;
    let rows = statement.query_map([item_id], |row| { let kind_json: String = row.get(1)?; let kind = serde_json::from_str(&kind_json).map_err(|error| rusqlite::Error::FromSqlConversionFailure(1, rusqlite::types::Type::Text, Box::new(error)))?; Ok(LocalAsset { asset_id: row.get(0)?, kind, relative_path: row.get(2)?, bytes: from_i64(row.get(3)?)?, sha256: row.get(4)?, mime_type: row.get(5)? }) }).map_err(db_error)?;
    rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
}

fn to_i64(value: u64) -> Result<i64, CoreError> { i64::try_from(value).map_err(|_| CoreError::new(ErrorKind::Persistence, "numeric value exceeds SQLite range", false)) }
fn from_i64(value: i64) -> rusqlite::Result<u64> { u64::try_from(value).map_err(|error| rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Integer, Box::new(error))) }
fn db_error(error: rusqlite::Error) -> CoreError { CoreError::new(ErrorKind::Persistence, format!("database error: {error}"), false) }
fn json_error(error: serde_json::Error) -> CoreError { CoreError::new(ErrorKind::Persistence, format!("serialization error: {error}"), false) }

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::MediaKind;
    use tempfile::tempdir;

    fn item(completed: bool) -> LibraryItem { LibraryItem { item_id: "item-1".into(), source: SourceIdentity { provider: "fixture".into(), media_id: "source-1".into(), canonical_url: Some("https://fixture.invalid/1".into()) }, display_title: "Test Video".into(), duration_ms: Some(60_000), quality_label: "720p".into(), assets: vec![LocalAsset { asset_id: "combined".into(), kind: MediaKind::Video, relative_path: "items/item-1/video.mp4".into(), bytes: 100, sha256: Some("abc".into()), mime_type: Some("video/mp4".into()) }], created_at_epoch_ms: 10, playback_position_ms: 0, completed } }

    #[test] fn fresh_database_migrates_to_current_schema() { let store = LibraryStore::open_in_memory().unwrap(); assert_eq!(store.schema_version().unwrap(), SCHEMA_VERSION); }
    #[test] fn promotion_is_atomic_and_searchable() { let store = LibraryStore::open_in_memory().unwrap(); let complete = item(true); store.stage_asset("job-1", &complete.assets[0]).unwrap(); store.promote_completed("job-1", &complete).unwrap(); assert!(store.staged_job_ids().unwrap().is_empty()); assert_eq!(store.list(Some("Video")).unwrap(), vec![complete]); }
    #[test] fn incomplete_item_cannot_be_promoted() { let store = LibraryStore::open_in_memory().unwrap(); let error = store.promote_completed("job-1", &item(false)).unwrap_err(); assert_eq!(error.kind, ErrorKind::IntegrityFailure); assert!(store.list(None).unwrap().is_empty()); }
    #[test] fn playback_rename_and_delete_round_trip() { let store = LibraryStore::open_in_memory().unwrap(); store.promote_completed("job-1", &item(true)).unwrap(); assert!(store.rename("item-1", "Renamed").unwrap()); assert!(store.save_playback_position("item-1", 12_345).unwrap()); let updated = store.get("item-1").unwrap().unwrap(); assert_eq!(updated.display_title, "Renamed"); assert_eq!(updated.playback_position_ms, 12_345); let deleted = store.delete("item-1").unwrap().unwrap(); assert_eq!(deleted.item_id, "item-1"); assert!(store.get("item-1").unwrap().is_none()); }
    #[test] fn durable_download_snapshot_round_trips() { let store = LibraryStore::open_in_memory().unwrap(); let snapshot = DurableDownloadSnapshot { job_id: "job-1".into(), state: DownloadState::RetryWait, bytes_downloaded: 44, total_bytes: Some(100), attempt: 2, retry_at_epoch_ms: Some(500), last_error: Some(CoreError::new(ErrorKind::NetworkTimeout, "timed out", true)) }; store.save_download_snapshot(&snapshot).unwrap(); assert_eq!(store.load_download_snapshots().unwrap(), vec![snapshot]); }
    #[test] fn search_escapes_sql_like_wildcards() { let store = LibraryStore::open_in_memory().unwrap(); let mut percent = item(true); percent.display_title = "100% Offline".into(); store.promote_completed("job-1", &percent).unwrap(); assert_eq!(store.list(Some("100%" )).unwrap().len(), 1); assert!(store.list(Some("100_" )).unwrap().is_empty()); }
    #[test] fn rejects_traversal_asset_paths() { let store = LibraryStore::open_in_memory().unwrap(); let mut bad = item(true); bad.assets[0].relative_path = "../escape.mp4".into(); assert_eq!(store.promote_completed("job-1", &bad).unwrap_err().kind, ErrorKind::InvalidInput); }
    #[test] fn detects_missing_and_wrong_sized_assets() { let store = LibraryStore::open_in_memory().unwrap(); store.promote_completed("job-1", &item(true)).unwrap(); let root = tempdir().unwrap(); assert_eq!(store.validate_item_assets(root.path(), "item-1").unwrap_err().kind, ErrorKind::MissingAsset); let path = root.path().join("items/item-1"); std::fs::create_dir_all(&path).unwrap(); std::fs::write(path.join("video.mp4"), [0_u8; 3]).unwrap(); assert_eq!(store.validate_item_assets(root.path(), "item-1").unwrap_err().kind, ErrorKind::CorruptAsset); }
}
