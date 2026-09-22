use crate::{CoreError, DownloadState, DurableDownloadSnapshot, ErrorKind, LibraryStore};
use std::collections::HashSet;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct StartupReconciliation {
    pub jobs_requeued: usize,
    pub orphaned_staged_jobs_recovered: usize,
    pub asset_mismatches_reported: usize,
    pub orphaned_partial_files_removed: usize,
}

/// Reconcile durable queue state after an unclean process exit.
///
/// States that require an actively running worker cannot truthfully survive process death. They
/// are moved back to `Queued` so the Android service can explicitly reconstruct and restart them.
/// User-paused jobs remain paused, retry waits retain their durable deadline, and terminal states
/// are left untouched.
pub fn reconcile_startup_downloads(
    store: &LibraryStore,
) -> Result<StartupReconciliation, CoreError> {
    let snapshots = store.load_download_snapshots()?;
    let mut jobs_requeued = 0;
    for mut snapshot in snapshots {
        if requires_live_worker(snapshot.state) {
            snapshot.state = DownloadState::Queued;
            snapshot.retry_at_epoch_ms = None;
            snapshot.last_error = Some(CoreError::new(
                ErrorKind::Internal,
                "download was interrupted by process shutdown and has been queued for recovery",
                true,
            ));
            store.save_download_snapshot(&snapshot)?;
            jobs_requeued += 1;
        }
    }
    Ok(StartupReconciliation {
        jobs_requeued,
        ..StartupReconciliation::default()
    })
}

/// Reconcile startup state that requires access to the managed media root.
///
/// This extends durable queue recovery with conservative file-system reconciliation:
/// staged DB rows without a durable job are surfaced as retryable failed jobs, completed library
/// records whose metadata no longer matches files are surfaced as repairable recovery jobs, and
/// orphaned partial/resume files are removed only when there is no durable or staged recovery work
/// that could still legally claim them.
pub fn reconcile_startup_with_library_root(
    store: &LibraryStore,
    library_root: &Path,
) -> Result<StartupReconciliation, CoreError> {
    let mut result = reconcile_startup_downloads(store)?;
    result.orphaned_staged_jobs_recovered = reconcile_orphaned_staged_jobs(store)?;
    result.asset_mismatches_reported = reconcile_metadata_file_mismatches(store, library_root)?;
    result.orphaned_partial_files_removed =
        remove_orphaned_partial_files_when_safe(store, library_root)?;
    Ok(result)
}

const fn requires_live_worker(state: DownloadState) -> bool {
    matches!(
        state,
        DownloadState::Resolving | DownloadState::Downloading | DownloadState::Verifying
    )
}

const fn is_terminal(state: DownloadState) -> bool {
    matches!(
        state,
        DownloadState::Completed | DownloadState::Canceled | DownloadState::Failed
    )
}

fn reconcile_orphaned_staged_jobs(store: &LibraryStore) -> Result<usize, CoreError> {
    let snapshots = store.load_download_snapshots()?;
    let durable_job_ids = snapshots
        .iter()
        .map(|snapshot| snapshot.job_id.as_str())
        .collect::<HashSet<_>>();
    let mut recovered = 0;
    for job_id in store.staged_job_ids()? {
        if durable_job_ids.contains(job_id.as_str()) {
            continue;
        }
        store.save_download_snapshot(&DurableDownloadSnapshot {
            job_id: job_id.clone(),
            state: DownloadState::Failed,
            bytes_downloaded: 0,
            total_bytes: None,
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: Some(CoreError::new(
                ErrorKind::MissingAsset,
                "staged download metadata had no durable queue entry and needs user retry",
                true,
            )),
        })?;
        recovered += 1;
    }
    Ok(recovered)
}

fn reconcile_metadata_file_mismatches(
    store: &LibraryStore,
    library_root: &Path,
) -> Result<usize, CoreError> {
    let mut mismatches = 0;
    for item in store.list(None)? {
        if !item.completed {
            continue;
        }
        if let Err(error) = store.validate_item_assets(library_root, &item.item_id) {
            if matches!(
                error.kind,
                ErrorKind::MissingAsset | ErrorKind::CorruptAsset
            ) {
                store.save_download_snapshot(&DurableDownloadSnapshot {
                    job_id: format!("repair:{}", item.item_id),
                    state: DownloadState::Failed,
                    bytes_downloaded: 0,
                    total_bytes: None,
                    attempt: 0,
                    retry_at_epoch_ms: None,
                    last_error: Some(CoreError::new(
                        error.kind,
                        format!(
                            "completed library item {} requires asset recovery",
                            item.item_id
                        ),
                        true,
                    )),
                })?;
                mismatches += 1;
            } else {
                return Err(error);
            }
        }
    }
    Ok(mismatches)
}

fn remove_orphaned_partial_files_when_safe(
    store: &LibraryStore,
    library_root: &Path,
) -> Result<usize, CoreError> {
    let snapshots = store.load_download_snapshots()?;
    if snapshots
        .iter()
        .any(|snapshot| !is_terminal(snapshot.state))
        || !store.staged_job_ids()?.is_empty()
    {
        return Ok(0);
    }
    let mut partials = Vec::new();
    collect_partial_files(library_root, &mut partials)?;
    let mut removed = 0;
    for path in partials {
        std::fs::remove_file(&path).map_err(|error| {
            CoreError::new(
                ErrorKind::Persistence,
                format!(
                    "failed to remove orphaned partial file {}: {error}",
                    path.display()
                ),
                true,
            )
        })?;
        removed += 1;
    }
    Ok(removed)
}

fn collect_partial_files(root: &Path, partials: &mut Vec<PathBuf>) -> Result<(), CoreError> {
    if !root.exists() {
        return Ok(());
    }
    for entry in std::fs::read_dir(root).map_err(|error| {
        CoreError::new(
            ErrorKind::Persistence,
            format!(
                "failed to inspect managed media root {}: {error}",
                root.display()
            ),
            true,
        )
    })? {
        let entry = entry.map_err(|error| {
            CoreError::new(
                ErrorKind::Persistence,
                format!("failed to inspect managed media root entry: {error}"),
                true,
            )
        })?;
        let path = entry.path();
        let metadata = entry.metadata().map_err(|error| {
            CoreError::new(
                ErrorKind::Persistence,
                format!(
                    "failed to inspect managed media path {}: {error}",
                    path.display()
                ),
                true,
            )
        })?;
        if metadata.is_dir() {
            collect_partial_files(&path, partials)?;
        } else if metadata.is_file()
            && path
                .file_name()
                .and_then(|value| value.to_str())
                .is_some_and(|name| name.ends_with(".partial") || name.ends_with(".resume.json"))
        {
            partials.push(path);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LocalAsset, MediaKind, SourceIdentity};

    fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: job_id.into(),
            state,
            bytes_downloaded: 128,
            total_bytes: Some(1024),
            attempt: 2,
            retry_at_epoch_ms: Some(500),
            last_error: None,
        }
    }

    #[test]
    fn requeues_only_states_that_require_a_live_worker() {
        let store = LibraryStore::open_in_memory().unwrap();
        let states = [
            DownloadState::Queued,
            DownloadState::Resolving,
            DownloadState::Downloading,
            DownloadState::Paused,
            DownloadState::RetryWait,
            DownloadState::Failed,
            DownloadState::Verifying,
            DownloadState::Completed,
            DownloadState::Canceled,
        ];
        for (index, state) in states.into_iter().enumerate() {
            store
                .save_download_snapshot(&snapshot(&format!("job-{index}"), state))
                .unwrap();
        }

        let result = reconcile_startup_downloads(&store).unwrap();
        assert_eq!(result.jobs_requeued, 3);
        let reconciled = store.load_download_snapshots().unwrap();
        for (index, before) in states.into_iter().enumerate() {
            let after = reconciled
                .iter()
                .find(|value| value.job_id == format!("job-{index}"))
                .unwrap();
            if requires_live_worker(before) {
                assert_eq!(after.state, DownloadState::Queued);
                assert!(after.retry_at_epoch_ms.is_none());
                assert!(
                    after
                        .last_error
                        .as_ref()
                        .is_some_and(|error| error.retryable)
                );
            } else {
                assert_eq!(after.state, before);
                assert_eq!(after.retry_at_epoch_ms, Some(500));
                assert!(after.last_error.is_none());
            }
        }
    }

    #[test]
    fn reconciliation_is_idempotent() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("active", DownloadState::Downloading))
            .unwrap();
        assert_eq!(
            reconcile_startup_downloads(&store).unwrap().jobs_requeued,
            1
        );
        assert_eq!(
            reconcile_startup_downloads(&store).unwrap().jobs_requeued,
            0
        );
    }

    #[test]
    fn file_backed_reconciliation_recovers_orphaned_staged_jobs() {
        let temp = tempfile::tempdir().unwrap();
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .stage_asset(
                "staged-only",
                &LocalAsset {
                    asset_id: "video".into(),
                    kind: MediaKind::Video,
                    relative_path: "staged/video.mp4.partial".into(),
                    bytes: 12,
                    sha256: None,
                    mime_type: None,
                },
            )
            .unwrap();

        let result = reconcile_startup_with_library_root(&store, temp.path()).unwrap();
        assert_eq!(result.orphaned_staged_jobs_recovered, 1);
        let recovered = store
            .load_download_snapshots()
            .unwrap()
            .into_iter()
            .find(|snapshot| snapshot.job_id == "staged-only")
            .unwrap();
        assert_eq!(recovered.state, DownloadState::Failed);
        assert!(
            recovered
                .last_error
                .as_ref()
                .is_some_and(|error| error.retryable)
        );
    }

    #[test]
    fn file_backed_reconciliation_reports_completed_item_asset_mismatches() {
        let temp = tempfile::tempdir().unwrap();
        let store = LibraryStore::open_in_memory().unwrap();
        let item = crate::LibraryItem {
            item_id: "missing-media".into(),
            source: SourceIdentity::new("fixture", "missing-media"),
            display_title: "Missing Media".into(),
            duration_ms: None,
            quality_label: "fixture".into(),
            assets: vec![LocalAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                relative_path: "media/missing.mp4".into(),
                bytes: 100,
                sha256: None,
                mime_type: None,
            }],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        };
        store.promote_completed("job", &item).unwrap();

        let result = reconcile_startup_with_library_root(&store, temp.path()).unwrap();
        assert_eq!(result.asset_mismatches_reported, 1);
        let repair = store
            .load_download_snapshots()
            .unwrap()
            .into_iter()
            .find(|snapshot| snapshot.job_id == "repair:missing-media")
            .unwrap();
        assert_eq!(repair.state, DownloadState::Failed);
        assert_eq!(repair.last_error.unwrap().kind, ErrorKind::MissingAsset);
    }

    #[test]
    fn file_backed_reconciliation_removes_partial_files_only_when_no_recovery_claims_exist() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join("orphan.partial");
        std::fs::write(&partial, b"partial").unwrap();
        let store = LibraryStore::open_in_memory().unwrap();

        let result = reconcile_startup_with_library_root(&store, temp.path()).unwrap();
        assert_eq!(result.orphaned_partial_files_removed, 1);
        assert!(!partial.exists());

        let claimed = temp.path().join("claimed.partial");
        std::fs::write(&claimed, b"partial").unwrap();
        store
            .save_download_snapshot(&snapshot("active", DownloadState::Queued))
            .unwrap();
        let result = reconcile_startup_with_library_root(&store, temp.path()).unwrap();
        assert_eq!(result.orphaned_partial_files_removed, 0);
        assert!(claimed.exists());
    }
}
