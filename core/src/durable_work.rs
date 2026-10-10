use crate::{CoreError, DownloadPlan, ErrorKind};
use rusqlite::{Connection, params};
use std::path::Path;
use std::sync::{Arc, Mutex, MutexGuard};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DurableDownloadWorkItem {
    pub job_id: String,
    pub plan: DownloadPlan,
    pub created_at_epoch_ms: u64,
}

/// Process-death-safe executable work-plan storage used by Android runtime launch points.
///
/// Queue state remains authoritative for eligibility/control. The presentation table deliberately
/// outlives executable work deletion so completed Downloads rows retain resolved metadata after the
/// worker has discarded the no-longer-executable plan.
#[derive(Debug, Clone)]
pub struct DurableDownloadWorkStore {
    connection: Arc<Mutex<Connection>>,
}

impl DurableDownloadWorkStore {
    pub fn open(path: impl AsRef<Path>) -> Result<Self, CoreError> {
        let connection = Connection::open(path).map_err(db_error)?;
        connection
            .execute_batch(
                "CREATE TABLE IF NOT EXISTS download_work_items (
                   job_id TEXT PRIMARY KEY,
                   plan_json TEXT NOT NULL,
                   created_at_epoch_ms INTEGER NOT NULL
                 );
                 CREATE TABLE IF NOT EXISTS download_presentations (
                   job_id TEXT PRIMARY KEY,
                   display_title TEXT NOT NULL
                 );",
            )
            .map_err(db_error)?;
        Ok(Self {
            connection: Arc::new(Mutex::new(connection)),
        })
    }

    fn connection(&self) -> Result<MutexGuard<'_, Connection>, CoreError> {
        self.connection.lock().map_err(|_| {
            CoreError::new(
                ErrorKind::Persistence,
                "download work database lock poisoned",
                false,
            )
        })
    }

    pub fn save(&self, work: &DurableDownloadWorkItem) -> Result<(), CoreError> {
        if work.job_id.trim().is_empty() {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "download work job id must not be empty",
                false,
            ));
        }
        let plan_json = serde_json::to_string(&work.plan).map_err(json_error)?;
        let connection = self.connection()?;
        connection
            .execute(
                "INSERT INTO download_work_items(job_id, plan_json, created_at_epoch_ms)
                 VALUES(?1, ?2, ?3)
                 ON CONFLICT(job_id) DO UPDATE SET
                   plan_json=excluded.plan_json,
                   created_at_epoch_ms=excluded.created_at_epoch_ms",
                params![work.job_id, plan_json, to_i64(work.created_at_epoch_ms)?],
            )
            .map_err(db_error)?;
        connection
            .execute(
                "INSERT INTO download_presentations(job_id, display_title)
                 VALUES(?1, ?2)
                 ON CONFLICT(job_id) DO UPDATE SET display_title=excluded.display_title",
                params![work.job_id, work.plan.title],
            )
            .map_err(db_error)?;
        Ok(())
    }

    pub fn load_all(&self) -> Result<Vec<DurableDownloadWorkItem>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection
            .prepare(
                "SELECT job_id, plan_json, created_at_epoch_ms
                 FROM download_work_items ORDER BY job_id",
            )
            .map_err(db_error)?;
        let rows = statement
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, i64>(2)?,
                ))
            })
            .map_err(db_error)?;
        let mut work = Vec::new();
        for row in rows {
            let (job_id, plan_json, created_at_epoch_ms) = row.map_err(db_error)?;
            work.push(DurableDownloadWorkItem {
                job_id,
                plan: serde_json::from_str(&plan_json).map_err(json_error)?,
                created_at_epoch_ms: u64::try_from(created_at_epoch_ms).map_err(|_| {
                    CoreError::new(
                        ErrorKind::Persistence,
                        "negative created_at_epoch_ms in durable download work",
                        false,
                    )
                })?,
            });
        }
        Ok(work)
    }

    pub fn load_presentations(&self) -> Result<Vec<(String, String)>, CoreError> {
        let connection = self.connection()?;
        let mut statement = connection
            .prepare("SELECT job_id, display_title FROM download_presentations ORDER BY job_id")
            .map_err(db_error)?;
        let rows = statement
            .query_map([], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            })
            .map_err(db_error)?;
        rows.map(|row| row.map_err(db_error)).collect()
    }

    pub fn delete(&self, job_id: &str) -> Result<bool, CoreError> {
        let changed = self
            .connection()?
            .execute("DELETE FROM download_work_items WHERE job_id=?1", [job_id])
            .map_err(db_error)?;
        Ok(changed == 1)
    }
}

fn to_i64(value: u64) -> Result<i64, CoreError> {
    i64::try_from(value).map_err(|_| {
        CoreError::new(
            ErrorKind::Persistence,
            "download work integer exceeds SQLite range",
            false,
        )
    })
}

fn db_error(error: rusqlite::Error) -> CoreError {
    CoreError::new(ErrorKind::Persistence, error.to_string(), false)
}

fn json_error(error: serde_json::Error) -> CoreError {
    CoreError::new(ErrorKind::Persistence, error.to_string(), false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Compatibility, DownloadPlanAsset, MediaKind, QualityChoice, SourceIdentity};
    use tempfile::tempdir;

    fn work() -> DurableDownloadWorkItem {
        DurableDownloadWorkItem {
            job_id: "fixture-job".into(),
            plan: DownloadPlan {
                source: SourceIdentity::new("fixture", "media-1"),
                title: "Fixture".into(),
                duration_ms: Some(42_000),
                quality: QualityChoice {
                    choice_id: "720p".into(),
                    label: "720p".into(),
                    estimated_bytes: Some(37),
                    video_height: Some(720),
                    audio_only: false,
                    compatibility: Compatibility::Preferred,
                },
                assets: vec![DownloadPlanAsset {
                    asset_id: "video".into(),
                    kind: MediaKind::Video,
                    url: "http://127.0.0.1/fixture".into(),
                    relative_path: "items/fixture/video.mp4".into(),
                    expected_bytes: Some(37),
                    expected_sha256: None,
                    mime_type: Some("video/mp4".into()),
                }],
            },
            created_at_epoch_ms: 1234,
        }
    }

    #[test]
    fn executable_work_plan_survives_reopen_and_can_be_deleted() {
        let temp = tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let expected = work();

        DurableDownloadWorkStore::open(&database)
            .unwrap()
            .save(&expected)
            .unwrap();

        let reopened = DurableDownloadWorkStore::open(&database).unwrap();
        assert_eq!(reopened.load_all().unwrap(), vec![expected.clone()]);
        assert_eq!(
            reopened.load_presentations().unwrap(),
            vec![("fixture-job".into(), "Fixture".into())]
        );
        assert!(reopened.delete(&expected.job_id).unwrap());
        assert!(reopened.load_all().unwrap().is_empty());
        assert_eq!(
            reopened.load_presentations().unwrap(),
            vec![("fixture-job".into(), "Fixture".into())]
        );
    }

    #[test]
    fn rejects_blank_job_identity() {
        let temp = tempdir().unwrap();
        let mut invalid = work();
        invalid.job_id = " ".into();
        let error = DurableDownloadWorkStore::open(temp.path().join("library.sqlite3"))
            .unwrap()
            .save(&invalid)
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
    }
}
