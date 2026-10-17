use super::*;

impl LibraryStore {
    pub fn save_download_snapshot(
        &self,
        snapshot: &DurableDownloadSnapshot,
    ) -> Result<(), CoreError> {
        let state = serde_json::to_string(&snapshot.state).map_err(json_error)?;
        let error = snapshot
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;
        self.connection()?
            .execute(
                "INSERT INTO download_jobs(job_id, state_json, bytes_downloaded, total_bytes,
                     attempt, retry_at_epoch_ms, error_json)
                 VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7)
                 ON CONFLICT(job_id) DO UPDATE SET
                   state_json=excluded.state_json,
                   bytes_downloaded=excluded.bytes_downloaded,
                   total_bytes=excluded.total_bytes,
                   attempt=excluded.attempt,
                   retry_at_epoch_ms=excluded.retry_at_epoch_ms,
                   error_json=excluded.error_json",
                params![
                    snapshot.job_id,
                    state,
                    to_i64(snapshot.bytes_downloaded)?,
                    snapshot.total_bytes.map(to_i64).transpose()?,
                    i64::from(snapshot.attempt),
                    snapshot.retry_at_epoch_ms.map(to_i64).transpose()?,
                    error
                ],
            )
            .map_err(db_error)?;
        Ok(())
    }

    /// Enqueue a new queue-only job without replacing a concurrently created
    /// or already active job with the same identifier.
    pub(crate) fn insert_download_snapshot_if_absent(
        &self,
        snapshot: &DurableDownloadSnapshot,
    ) -> Result<bool, CoreError> {
        let state = serde_json::to_string(&snapshot.state).map_err(json_error)?;
        let error = snapshot
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;
        let changed = self
            .connection()?
            .execute(
                "INSERT INTO download_jobs (job_id, state_json, bytes_downloaded,
                 total_bytes, attempt, retry_at_epoch_ms, error_json)
                 VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7)
                 ON CONFLICT(job_id) DO NOTHING",
                params![
                    snapshot.job_id,
                    state,
                    to_i64(snapshot.bytes_downloaded)?,
                    snapshot.total_bytes.map(to_i64).transpose()?,
                    i64::from(snapshot.attempt),
                    snapshot.retry_at_epoch_ms.map(to_i64).transpose()?,
                    error
                ],
            )
            .map_err(db_error)?;
        Ok(changed == 1)
    }

    /// Atomically replace a previously observed durable row. Other SQLite connections
    /// (for example foreground and user-initiated Android workers) may have claimed or
    /// paused the job after it was read. A stale observation must never overwrite them.
    pub(crate) fn replace_download_snapshot_if_unchanged(
        &self,
        previous: &DurableDownloadSnapshot,
        next: &DurableDownloadSnapshot,
    ) -> Result<bool, CoreError> {
        if previous.job_id != next.job_id {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "durable download compare-and-swap cannot change job identity",
                false,
            ));
        }
        let previous_state = serde_json::to_string(&previous.state).map_err(json_error)?;
        let next_state = serde_json::to_string(&next.state).map_err(json_error)?;
        let previous_error = previous
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;
        let next_error = next
            .last_error
            .as_ref()
            .map(serde_json::to_string)
            .transpose()
            .map_err(json_error)?;

        let changed = self
            .connection()?
            .execute(
                "UPDATE download_jobs SET state_json=?1, bytes_downloaded=?2,
                total_bytes=?3, attempt=?4, retry_at_epoch_ms=?5, error_json=?6
             WHERE job_id=?7 AND state_json=?8 AND bytes_downloaded=?9
                AND total_bytes IS ?10 AND attempt=?11
                AND retry_at_epoch_ms IS ?12 AND error_json IS ?13",
                params![
                    next_state,
                    to_i64(next.bytes_downloaded)?,
                    next.total_bytes.map(to_i64).transpose()?,
                    i64::from(next.attempt),
                    next.retry_at_epoch_ms.map(to_i64).transpose()?,
                    next_error,
                    next.job_id,
                    previous_state,
                    to_i64(previous.bytes_downloaded)?,
                    previous.total_bytes.map(to_i64).transpose()?,
                    i64::from(previous.attempt),
                    previous.retry_at_epoch_ms.map(to_i64).transpose()?,
                    previous_error,
                ],
            )
            .map_err(db_error)?;
        Ok(changed == 1)
    }

    pub fn load_download_snapshots(&self) -> Result<Vec<DurableDownloadSnapshot>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection
            .prepare(
                "SELECT job_id, state_json, bytes_downloaded, total_bytes, attempt,
                        retry_at_epoch_ms, error_json
                 FROM download_jobs ORDER BY job_id",
            )
            .map_err(db_error)?;
        let rows = statement
            .query_map([], |row| {
                let state_json: String = row.get(1)?;
                let error_json: Option<String> = row.get(6)?;
                let state =
                    serde_json::from_str::<DownloadState>(&state_json).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            1,
                            rusqlite::types::Type::Text,
                            Box::new(error),
                        )
                    })?;
                let last_error = error_json
                    .map(|value| serde_json::from_str(&value))
                    .transpose()
                    .map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            6,
                            rusqlite::types::Type::Text,
                            Box::new(error),
                        )
                    })?;
                Ok(DurableDownloadSnapshot {
                    job_id: row.get(0)?,
                    state,
                    bytes_downloaded: from_i64(row.get(2)?)?,
                    total_bytes: row.get::<_, Option<i64>>(3)?.map(from_i64).transpose()?,
                    attempt: u32::try_from(row.get::<_, i64>(4)?).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            4,
                            rusqlite::types::Type::Integer,
                            Box::new(error),
                        )
                    })?,
                    retry_at_epoch_ms: row.get::<_, Option<i64>>(5)?.map(from_i64).transpose()?,
                    last_error,
                })
            })
            .map_err(db_error)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
    }

    pub fn staged_job_ids(&self) -> Result<Vec<String>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection
            .prepare("SELECT DISTINCT job_id FROM staged_assets ORDER BY job_id")
            .map_err(db_error)?;
        let rows = statement
            .query_map([], |row| row.get(0))
            .map_err(db_error)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
    }
}
