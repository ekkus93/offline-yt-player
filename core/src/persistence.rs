use crate::domain::{CoreError, ErrorKind, LibraryItem, LocalAsset, SourceIdentity};
use crate::events::DurableDownloadSnapshot;
use crate::state::DownloadState;
use rusqlite::{Connection, OptionalExtension, Transaction, params};
use std::path::{Component, Path};
use std::sync::{Arc, Mutex, MutexGuard};

mod rows;
mod snapshots;
#[cfg(test)]
mod tests;

use rows::*;

pub const SCHEMA_VERSION: i64 = 1;

fn has_playable_media_asset(item: &LibraryItem) -> bool {
    item.assets.iter().any(|asset| {
        matches!(
            asset.kind,
            crate::domain::MediaKind::Video | crate::domain::MediaKind::Audio
        )
    })
}

#[derive(Debug, Clone)]
pub struct LibraryStore {
    connection: Arc<Mutex<Connection>>,
}

impl LibraryStore {
    pub fn open(path: impl AsRef<Path>) -> Result<Self, CoreError> {
        let connection = Connection::open(path).map_err(db_error)?;
        let store = Self {
            connection: Arc::new(Mutex::new(connection)),
        };
        store.migrate()?;
        Ok(store)
    }

    pub fn open_in_memory() -> Result<Self, CoreError> {
        let connection = Connection::open_in_memory().map_err(db_error)?;
        let store = Self {
            connection: Arc::new(Mutex::new(connection)),
        };
        store.migrate()?;
        Ok(store)
    }

    fn connection(&self) -> Result<MutexGuard<'_, Connection>, CoreError> {
        self.connection.lock().map_err(|_| {
            CoreError::new(
                ErrorKind::Persistence,
                "library database lock poisoned",
                false,
            )
        })
    }

    pub fn migrate(&self) -> Result<(), CoreError> {
        let connection = self.connection()?;
        connection
            .execute_batch(
                "PRAGMA foreign_keys = ON;
                 CREATE TABLE IF NOT EXISTS schema_meta (
                   id INTEGER PRIMARY KEY CHECK (id = 1),
                   version INTEGER NOT NULL
                 );
                 INSERT OR IGNORE INTO schema_meta(id, version) VALUES(1, 0);",
            )
            .map_err(db_error)?;
        let version: i64 = connection
            .query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| {
                row.get(0)
            })
            .map_err(db_error)?;
        if version > SCHEMA_VERSION {
            return Err(CoreError::new(
                ErrorKind::Persistence,
                "library database is newer than this application",
                false,
            ));
        }
        if version < 1 {
            connection
                .execute_batch(
                    "BEGIN IMMEDIATE;
                     CREATE TABLE library_items (
                       item_id TEXT PRIMARY KEY,
                       provider TEXT NOT NULL,
                       source_media_id TEXT NOT NULL,
                       canonical_url TEXT,
                       display_title TEXT NOT NULL,
                       duration_ms INTEGER,
                       quality_label TEXT NOT NULL,
                       created_at_epoch_ms INTEGER NOT NULL,
                       playback_position_ms INTEGER NOT NULL DEFAULT 0,
                       completed INTEGER NOT NULL DEFAULT 0
                     );
                     CREATE UNIQUE INDEX library_source_identity
                       ON library_items(provider, source_media_id);
                     CREATE TABLE assets (
                       item_id TEXT NOT NULL REFERENCES library_items(item_id) ON DELETE CASCADE,
                       asset_id TEXT NOT NULL,
                       kind_json TEXT NOT NULL,
                       relative_path TEXT NOT NULL,
                       bytes INTEGER NOT NULL,
                       sha256 TEXT,
                       mime_type TEXT,
                       PRIMARY KEY(item_id, asset_id)
                     );
                     CREATE TABLE staged_assets (
                       job_id TEXT NOT NULL,
                       asset_id TEXT NOT NULL,
                       relative_path TEXT NOT NULL,
                       bytes INTEGER NOT NULL,
                       sha256 TEXT,
                       PRIMARY KEY(job_id, asset_id)
                     );
                     CREATE TABLE download_jobs (
                       job_id TEXT PRIMARY KEY,
                       state_json TEXT NOT NULL,
                       bytes_downloaded INTEGER NOT NULL,
                       total_bytes INTEGER,
                       attempt INTEGER NOT NULL,
                       retry_at_epoch_ms INTEGER,
                       error_json TEXT
                     );
                     UPDATE schema_meta SET version=1 WHERE id=1;
                     COMMIT;",
                )
                .map_err(db_error)?;
        }
        Ok(())
    }

    pub fn schema_version(&self) -> Result<i64, CoreError> {
        self.connection()?
            .query_row("SELECT version FROM schema_meta WHERE id=1", [], |row| {
                row.get(0)
            })
            .map_err(db_error)
    }

    pub fn stage_asset(&self, job_id: &str, asset: &LocalAsset) -> Result<(), CoreError> {
        validate_relative_asset_path(&asset.relative_path)?;
        self.connection()?
            .execute(
                "INSERT INTO staged_assets(job_id, asset_id, relative_path, bytes, sha256)
                 VALUES(?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(job_id, asset_id) DO UPDATE SET
                   relative_path=excluded.relative_path,
                   bytes=excluded.bytes,
                   sha256=excluded.sha256",
                params![
                    job_id,
                    asset.asset_id,
                    asset.relative_path,
                    to_i64(asset.bytes)?,
                    asset.sha256
                ],
            )
            .map_err(db_error)?;
        Ok(())
    }

    /// Atomically promotes the metadata for a verified item and clears its staging records.
    /// Files must already have been durably moved to their final relative paths.
    pub fn promote_completed(&self, job_id: &str, item: &LibraryItem) -> Result<(), CoreError> {
        if !item.completed || !has_playable_media_asset(item) {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "cannot promote an incomplete library item",
                false,
            ));
        }
        for asset in &item.assets {
            validate_relative_asset_path(&asset.relative_path)?;
        }
        let mut connection = self.connection()?;
        let transaction = connection.transaction().map_err(db_error)?;
        promote_item_transaction(&transaction, job_id, item)?;
        transaction.commit().map_err(db_error)
    }

    /// Verify the exact durable worker state and promote Library metadata in
    /// the same SQLite write transaction. Concurrent pause/cancel cannot be
    /// undone by a stale completion, nor can it yield a promoted canceled item.
    pub(crate) fn promote_completed_if_unchanged(
        &self,
        job_id: &str,
        item: &LibraryItem,
        previous: &DurableDownloadSnapshot,
        completed: &DurableDownloadSnapshot,
    ) -> Result<bool, CoreError> {
        if previous.job_id != job_id
            || completed.job_id != job_id
            || item.item_id != job_id
            || previous.state != DownloadState::Verifying
            || completed.state != DownloadState::Completed
        {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "durable completion identity/state mismatch",
                false,
            ));
        }
        if !item.completed || !has_playable_media_asset(item) {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "cannot promote an incomplete library item",
                false,
            ));
        }
        for asset in &item.assets {
            validate_relative_asset_path(&asset.relative_path)?;
        }
        let previous_state = serde_json::to_string(&previous.state).map_err(json_error)?;
        let completed_state = serde_json::to_string(&completed.state).map_err(json_error)?;
        let previous_error = previous
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;
        let completed_error = completed
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;

        let mut connection = self.connection()?;
        let transaction = connection.transaction().map_err(db_error)?;
        let changed = transaction
            .execute(
                "UPDATE download_jobs SET state_json=?1, bytes_downloaded=?2,
                total_bytes=?3, attempt=?4, retry_at_epoch_ms=?5, error_json=?6
             WHERE job_id=?7 AND state_json=?8 AND bytes_downloaded=?9
                AND total_bytes IS ?10 AND attempt=?11
                AND retry_at_epoch_ms IS ?12 AND error_json IS ?13",
                params![
                    completed_state,
                    to_i64(completed.bytes_downloaded)?,
                    completed.total_bytes.map(to_i64).transpose()?,
                    i64::from(completed.attempt),
                    completed.retry_at_epoch_ms.map(to_i64).transpose()?,
                    completed_error,
                    job_id,
                    previous_state,
                    to_i64(previous.bytes_downloaded)?,
                    previous.total_bytes.map(to_i64).transpose()?,
                    i64::from(previous.attempt),
                    previous.retry_at_epoch_ms.map(to_i64).transpose()?,
                    previous_error,
                ],
            )
            .map_err(db_error)?;
        if changed == 0 {
            return Ok(false);
        }
        promote_item_transaction(&transaction, job_id, item)?;
        transaction.commit().map_err(db_error)?;
        Ok(true)
    }

    pub fn list(&self, query: Option<&str>) -> Result<Vec<LibraryItem>, CoreError> {
        let connection = self.connection()?;
        let like = query.map(|value| format!("%{}%", escape_like(value)));
        let mut statement = connection
            .prepare(
                "SELECT item_id, provider, source_media_id, canonical_url, display_title,
                        duration_ms, quality_label, created_at_epoch_ms,
                        playback_position_ms, completed
                 FROM library_items
                 WHERE (?1 IS NULL OR display_title LIKE ?1 ESCAPE '\\')
                 ORDER BY created_at_epoch_ms DESC",
            )
            .map_err(db_error)?;
        let rows = statement
            .query_map([like.as_deref()], map_item_row)
            .map_err(db_error)?;
        let mut items = Vec::new();
        for row in rows {
            let mut item = row.map_err(db_error)?;
            item.assets = load_assets(&connection, &item.item_id)?;
            items.push(item);
        }
        Ok(items)
    }

    pub fn get(&self, item_id: &str) -> Result<Option<LibraryItem>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection
            .prepare(
                "SELECT item_id, provider, source_media_id, canonical_url, display_title,
                        duration_ms, quality_label, created_at_epoch_ms,
                        playback_position_ms, completed
                 FROM library_items WHERE item_id=?1",
            )
            .map_err(db_error)?;
        let mut item = statement
            .query_row([item_id], map_item_row)
            .optional()
            .map_err(db_error)?;
        if let Some(found) = &mut item {
            found.assets = load_assets(&connection, item_id)?;
        }
        Ok(item)
    }

    pub fn rename(&self, item_id: &str, title: &str) -> Result<bool, CoreError> {
        let changed = self
            .connection()?
            .execute(
                "UPDATE library_items SET display_title=?2 WHERE item_id=?1",
                params![item_id, title],
            )
            .map_err(db_error)?;
        Ok(changed == 1)
    }

    pub fn save_playback_position(
        &self,
        item_id: &str,
        position_ms: u64,
    ) -> Result<bool, CoreError> {
        let changed = self
            .connection()?
            .execute(
                "UPDATE library_items SET playback_position_ms=?2 WHERE item_id=?1",
                params![item_id, to_i64(position_ms)?],
            )
            .map_err(db_error)?;
        Ok(changed == 1)
    }

    pub fn delete(&self, item_id: &str) -> Result<Option<LibraryItem>, CoreError> {
        let item = self.get(item_id)?;
        if item.is_some() {
            self.connection()?
                .execute("DELETE FROM library_items WHERE item_id=?1", [item_id])
                .map_err(db_error)?;
        }
        Ok(item)
    }

    pub fn validate_item_assets(
        &self,
        library_root: &Path,
        item_id: &str,
    ) -> Result<(), CoreError> {
        let item = self.get(item_id)?.ok_or_else(|| {
            CoreError::new(
                ErrorKind::MissingAsset,
                "library item does not exist",
                false,
            )
        })?;
        // Reuse the same symlink-safe, stored-hash-aware validator as other
        // library inspection paths. Independent metadata() calls would follow
        // symlinks and allow same-length content corruption to pass silently.
        let validations = crate::validate_library_item_assets(
            library_root,
            &item,
            crate::AssetValidationDepth::Deep,
        )?;
        for validation in validations {
            match validation.health {
                crate::AssetHealth::Healthy => {}
                crate::AssetHealth::Missing => {
                    return Err(CoreError::new(
                        ErrorKind::MissingAsset,
                        format!("missing local asset {}", validation.asset_id),
                        false,
                    ));
                }
                crate::AssetHealth::Corrupt { .. } => {
                    return Err(CoreError::new(
                        ErrorKind::CorruptAsset,
                        format!(
                            "local asset {} failed integrity validation",
                            validation.asset_id
                        ),
                        false,
                    ));
                }
            }
        }
        Ok(())
    }
}
