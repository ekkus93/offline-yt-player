use crate::events::DurableDownloadSnapshot;
use crate::state::DownloadState;
use std::collections::HashSet;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StartupDisposition {
    /// Work that was active when the process died must re-enter through normal queue admission.
    RequeueInterrupted,
    /// A user pause remains paused across restart.
    PreservePaused,
    /// Retry timing remains durable and is evaluated by the scheduler.
    PreserveRetryWait,
    /// A job that had not started remains queued.
    PreserveQueued,
    /// Terminal records are not restarted.
    PreserveTerminal,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StartupJobPlan {
    pub job_id: String,
    pub persisted_state: DownloadState,
    pub disposition: StartupDisposition,
    pub has_staged_assets: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StartupRecoveryPlan {
    pub jobs: Vec<StartupJobPlan>,
    /// Staging rows with no durable job are orphaned metadata and may be cleaned safely.
    pub orphan_staged_job_ids: Vec<String>,
}

/// Reconciles durable queue snapshots with staged-asset ownership after process death.
///
/// This function is intentionally pure: the platform/service layer loads snapshots and staged job
/// IDs from `LibraryStore`, applies this plan, and separately reconciles filesystem `.partial`
/// assets before restarting transfer work.
#[must_use]
pub fn plan_startup_recovery(
    snapshots: &[DurableDownloadSnapshot],
    staged_job_ids: &[String],
) -> StartupRecoveryPlan {
    let durable_ids: HashSet<&str> = snapshots.iter().map(|snapshot| snapshot.job_id.as_str()).collect();
    let staged_ids: HashSet<&str> = staged_job_ids.iter().map(String::as_str).collect();

    let jobs = snapshots
        .iter()
        .map(|snapshot| StartupJobPlan {
            job_id: snapshot.job_id.clone(),
            persisted_state: snapshot.state,
            disposition: disposition(snapshot.state),
            has_staged_assets: staged_ids.contains(snapshot.job_id.as_str()),
        })
        .collect();

    let mut orphan_staged_job_ids: Vec<String> = staged_job_ids
        .iter()
        .filter(|job_id| !durable_ids.contains(job_id.as_str()))
        .cloned()
        .collect();
    orphan_staged_job_ids.sort();
    orphan_staged_job_ids.dedup();

    StartupRecoveryPlan {
        jobs,
        orphan_staged_job_ids,
    }
}

const fn disposition(state: DownloadState) -> StartupDisposition {
    match state {
        DownloadState::Queued => StartupDisposition::PreserveQueued,
        DownloadState::Resolving | DownloadState::Downloading | DownloadState::Verifying => {
            StartupDisposition::RequeueInterrupted
        }
        DownloadState::Paused => StartupDisposition::PreservePaused,
        DownloadState::RetryWait => StartupDisposition::PreserveRetryWait,
        DownloadState::Failed | DownloadState::Completed | DownloadState::Canceled => {
            StartupDisposition::PreserveTerminal
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
        DurableDownloadSnapshot {
            job_id: job_id.into(),
            state,
            bytes_downloaded: 0,
            total_bytes: None,
            attempt: 0,
            retry_at_epoch_ms: None,
            last_error: None,
        }
    }

    #[test]
    fn interrupted_active_states_are_requeued_but_user_pause_is_preserved() {
        let snapshots = vec![
            snapshot("resolving", DownloadState::Resolving),
            snapshot("downloading", DownloadState::Downloading),
            snapshot("verifying", DownloadState::Verifying),
            snapshot("paused", DownloadState::Paused),
        ];
        let plan = plan_startup_recovery(&snapshots, &[]);
        assert_eq!(plan.jobs[0].disposition, StartupDisposition::RequeueInterrupted);
        assert_eq!(plan.jobs[1].disposition, StartupDisposition::RequeueInterrupted);
        assert_eq!(plan.jobs[2].disposition, StartupDisposition::RequeueInterrupted);
        assert_eq!(plan.jobs[3].disposition, StartupDisposition::PreservePaused);
    }

    #[test]
    fn terminal_and_retry_states_are_not_accidentally_restarted() {
        for state in [DownloadState::Failed, DownloadState::Completed, DownloadState::Canceled] {
            let plan = plan_startup_recovery(&[snapshot("job", state)], &[]);
            assert_eq!(plan.jobs[0].disposition, StartupDisposition::PreserveTerminal);
        }
        let plan = plan_startup_recovery(&[snapshot("retry", DownloadState::RetryWait)], &[]);
        assert_eq!(plan.jobs[0].disposition, StartupDisposition::PreserveRetryWait);
    }

    #[test]
    fn staged_assets_are_correlated_and_orphans_are_reported_deterministically() {
        let snapshots = vec![snapshot("known", DownloadState::Downloading)];
        let staged = vec!["orphan-b".into(), "known".into(), "orphan-a".into(), "orphan-a".into()];
        let plan = plan_startup_recovery(&snapshots, &staged);
        assert!(plan.jobs[0].has_staged_assets);
        assert_eq!(plan.orphan_staged_job_ids, vec!["orphan-a", "orphan-b"]);
    }
}
