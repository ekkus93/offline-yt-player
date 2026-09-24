use crate::{
    CoreError, CoreEvent, DownloadEngine, DownloadPlan, DownloadPolicy, DownloadState,
    DownloadStateMachine, DurableDownloadSnapshot, DurableStopReason, ErrorKind, LibraryItem,
    LibraryStore, LocalAsset, PAUSE_POLL_INTERVAL, ProgressCoalescer, TransferMetricEstimator,
    TransferRequest, bounded_download_concurrency, durable_stop_reason, propagate_durable_stop,
    retry_delay,
};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};

#[derive(Debug, Clone)]
pub struct DownloadWorkItem {
    pub job_id: String,
    pub plan: DownloadPlan,
    pub created_at_epoch_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct DownloadWorkerReport {
    pub claimed: Vec<String>,
    pub completed: Vec<String>,
    pub paused: Vec<String>,
    pub failed: Vec<String>,
    pub retry_wait: Vec<String>,
    pub canceled: Vec<String>,
    pub repaired: Vec<String>,
    pub progress_events: Vec<CoreEvent>,
}

pub struct DownloadWorker {
    library: LibraryStore,
    library_root: PathBuf,
    policy: DownloadPolicy,
    max_concurrent: usize,
}

impl DownloadWorker {
    #[must_use]
    pub fn new(
        library: LibraryStore,
        library_root: impl Into<PathBuf>,
        policy: DownloadPolicy,
        max_concurrent: usize,
    ) -> Self {
        Self {
            library,
            library_root: library_root.into(),
            policy,
            max_concurrent: bounded_download_concurrency(max_concurrent),
        }
    }

    pub fn execute_ready_at(
        &self,
        work_items: &[DownloadWorkItem],
        cancel: &AtomicBool,
        now_epoch_ms: u64,
    ) -> Result<DownloadWorkerReport, CoreError> {
        let mut report = DownloadWorkerReport::default();
        let claimed = self.claim_eligible(work_items, now_epoch_ms)?;
        let engine = DownloadEngine::new(&self.library_root, self.policy.clone())?;

        for (mut snapshot, item) in claimed {
            report.claimed.push(snapshot.job_id.clone());
            if cancel.load(Ordering::Relaxed) {
                self.finish_canceled(&mut snapshot)?;
                report.canceled.push(snapshot.job_id.clone());
                continue;
            }
            match self.execute_one(&engine, item, &mut snapshot, cancel, now_epoch_ms) {
                Ok(progress_events) => {
                    report.progress_events.extend(progress_events);
                    report.completed.push(snapshot.job_id.clone());
                }
                Err(error) if error.kind == ErrorKind::Canceled => {
                    match durable_stop_reason(&self.library, &snapshot.job_id)? {
                        Some(DurableStopReason::Pause) => {
                            report.paused.push(snapshot.job_id.clone());
                        }
                        _ => {
                            self.finish_canceled(&mut snapshot)?;
                            report.canceled.push(snapshot.job_id.clone());
                        }
                    }
                }
                Err(error) => {
                    let retryable = error.retryable && snapshot.attempt < self.policy.max_attempts;
                    self.finish_failed_or_retry(&mut snapshot, error, retryable, now_epoch_ms)?;
                    if retryable {
                        report.retry_wait.push(snapshot.job_id.clone());
                    } else {
                        report.failed.push(snapshot.job_id.clone());
                    }
                }
            }
        }
        Ok(report)
    }

    pub fn repair_interrupted_claims_at(
        &self,
        now_epoch_ms: u64,
    ) -> Result<DownloadWorkerReport, CoreError> {
        let mut report = DownloadWorkerReport::default();
        for mut snapshot in self.library.load_download_snapshots()? {
            if matches!(
                snapshot.state,
                DownloadState::Resolving | DownloadState::Downloading | DownloadState::Verifying
            ) {
                transition_snapshot(&mut snapshot, DownloadState::RetryWait)?;
                snapshot.retry_at_epoch_ms = Some(now_epoch_ms);
                snapshot.last_error = Some(CoreError::new(
                    ErrorKind::NetworkUnavailable,
                    "Download was interrupted before completion",
                    true,
                ));
                self.library.save_download_snapshot(&snapshot)?;
                report.repaired.push(snapshot.job_id);
            }
        }
        Ok(report)
    }

    fn claim_eligible<'a>(
        &self,
        work_items: &'a [DownloadWorkItem],
        now_epoch_ms: u64,
    ) -> Result<Vec<(DurableDownloadSnapshot, &'a DownloadWorkItem)>, CoreError> {
        let by_id: HashMap<&str, &DownloadWorkItem> = work_items
            .iter()
            .map(|item| (item.job_id.as_str(), item))
            .collect();
        let mut claimed = Vec::new();
        for mut snapshot in self.library.load_download_snapshots()? {
            if claimed.len() >= self.max_concurrent {
                break;
            }
            let eligible = match snapshot.state {
                DownloadState::Queued => true,
                DownloadState::RetryWait => snapshot
                    .retry_at_epoch_ms
                    .is_none_or(|retry_at| retry_at <= now_epoch_ms),
                _ => false,
            };
            if !eligible {
                continue;
            }
            let Some(item) = by_id.get(snapshot.job_id.as_str()).copied() else {
                continue;
            };
            transition_snapshot(&mut snapshot, DownloadState::Resolving)?;
            snapshot.attempt = snapshot.attempt.saturating_add(1);
            snapshot.last_error = None;
            self.library.save_download_snapshot(&snapshot)?;
            claimed.push((snapshot, item));
        }
        Ok(claimed)
    }

    fn execute_one(
        &self,
        engine: &DownloadEngine,
        item: &DownloadWorkItem,
        snapshot: &mut DurableDownloadSnapshot,
        cancel: &AtomicBool,
        now_epoch_ms: u64,
    ) -> Result<Vec<CoreEvent>, CoreError> {
        transition_snapshot(snapshot, DownloadState::Downloading)?;
        snapshot.total_bytes = total_expected_bytes(&item.plan);
        self.library.save_download_snapshot(snapshot)?;

        let mut assets = Vec::new();
        let mut coalescer = ProgressCoalescer::default();
        let mut metric_estimator = TransferMetricEstimator::default();
        let mut progress_epoch_ms = now_epoch_ms;
        let mut progress_events = Vec::new();
        metric_estimator.observe(
            progress_epoch_ms,
            snapshot.bytes_downloaded,
            snapshot.total_bytes,
        );

        for plan_asset in &item.plan.assets {
            if cancel.load(Ordering::Relaxed) {
                return Err(CoreError::new(
                    ErrorKind::Canceled,
                    "Download canceled",
                    false,
                ));
            }
            let request = TransferRequest {
                url: plan_asset.url.clone(),
                relative_path: plan_asset.relative_path.clone(),
                expected_bytes: plan_asset.expected_bytes,
                expected_sha256: plan_asset.expected_sha256.clone(),
            };
            let result = self.transfer_with_durable_stop(engine, &item.job_id, &request, cancel)?;
            snapshot.bytes_downloaded = snapshot.bytes_downloaded.saturating_add(result.bytes);
            progress_epoch_ms = progress_epoch_ms.saturating_add(1_000);
            let metrics = metric_estimator.observe(
                progress_epoch_ms,
                snapshot.bytes_downloaded,
                snapshot.total_bytes,
            );
            if coalescer.should_emit(progress_epoch_ms, snapshot.bytes_downloaded, false) {
                progress_events.push(CoreEvent::DownloadProgress {
                    job_id: snapshot.job_id.clone(),
                    bytes_downloaded: snapshot.bytes_downloaded,
                    total_bytes: snapshot.total_bytes,
                    metrics,
                });
                self.library.save_download_snapshot(snapshot)?;
            }
            let local = LocalAsset {
                asset_id: plan_asset.asset_id.clone(),
                kind: plan_asset.kind,
                relative_path: result.relative_path,
                bytes: result.bytes,
                sha256: Some(result.sha256),
                mime_type: plan_asset.mime_type.clone(),
            };
            self.library.stage_asset(&item.job_id, &local)?;
            assets.push(local);
        }

        transition_snapshot(snapshot, DownloadState::Verifying)?;
        self.library.save_download_snapshot(snapshot)?;
        let library_item = LibraryItem {
            item_id: item.job_id.clone(),
            source: item.plan.source.clone(),
            display_title: item.plan.title.clone(),
            duration_ms: None,
            quality_label: item.plan.quality.label.clone(),
            assets,
            created_at_epoch_ms: item.created_at_epoch_ms,
            playback_position_ms: 0,
            completed: true,
        };
        self.library
            .promote_completed(&item.job_id, &library_item)?;
        transition_snapshot(snapshot, DownloadState::Completed)?;
        snapshot.bytes_downloaded = snapshot.total_bytes.unwrap_or(snapshot.bytes_downloaded);
        snapshot.retry_at_epoch_ms = None;
        snapshot.last_error = None;
        progress_epoch_ms = progress_epoch_ms.saturating_add(1_000);
        let metrics = metric_estimator.observe(
            progress_epoch_ms,
            snapshot.bytes_downloaded,
            snapshot.total_bytes,
        );
        if coalescer.should_emit(progress_epoch_ms, snapshot.bytes_downloaded, true) {
            progress_events.push(CoreEvent::DownloadProgress {
                job_id: snapshot.job_id.clone(),
                bytes_downloaded: snapshot.bytes_downloaded,
                total_bytes: snapshot.total_bytes,
                metrics,
            });
        }
        self.library.save_download_snapshot(snapshot)?;
        Ok(progress_events)
    }

    fn transfer_with_durable_stop(
        &self,
        engine: &DownloadEngine,
        job_id: &str,
        request: &TransferRequest,
        external_cancel: &AtomicBool,
    ) -> Result<crate::TransferResult, CoreError> {
        let transfer_stop = AtomicBool::new(false);
        let polling_done = AtomicBool::new(false);
        let poll_error = std::sync::Mutex::new(None);

        let result = std::thread::scope(|scope| {
            scope.spawn(|| {
                while !polling_done.load(Ordering::Acquire) {
                    if external_cancel.load(Ordering::Acquire) {
                        transfer_stop.store(true, Ordering::Release);
                        break;
                    }
                    if let Err(error) =
                        propagate_durable_stop(&self.library, job_id, &transfer_stop)
                    {
                        *poll_error.lock().expect("pause poll error mutex poisoned") = Some(error);
                        transfer_stop.store(true, Ordering::Release);
                        break;
                    }
                    if transfer_stop.load(Ordering::Acquire) {
                        break;
                    }
                    std::thread::sleep(PAUSE_POLL_INTERVAL);
                }
            });
            let transfer_result = engine.transfer(request, &transfer_stop);
            polling_done.store(true, Ordering::Release);
            transfer_result
        });

        if let Some(error) = poll_error
            .into_inner()
            .expect("pause poll error mutex poisoned")
        {
            return Err(error);
        }
        result
    }

    fn finish_canceled(&self, snapshot: &mut DurableDownloadSnapshot) -> Result<(), CoreError> {
        transition_snapshot(snapshot, DownloadState::Canceled)?;
        snapshot.retry_at_epoch_ms = None;
        snapshot.last_error = None;
        self.library.save_download_snapshot(snapshot)
    }

    fn finish_failed_or_retry(
        &self,
        snapshot: &mut DurableDownloadSnapshot,
        error: CoreError,
        retryable: bool,
        now_epoch_ms: u64,
    ) -> Result<(), CoreError> {
        if retryable {
            transition_snapshot(snapshot, DownloadState::RetryWait)?;
            let delay_ms =
                u64::try_from(retry_delay(snapshot.attempt, 0).as_millis()).unwrap_or(u64::MAX);
            snapshot.retry_at_epoch_ms = Some(now_epoch_ms.saturating_add(delay_ms));
        } else {
            transition_snapshot(snapshot, DownloadState::Failed)?;
            snapshot.retry_at_epoch_ms = None;
        }
        snapshot.last_error = Some(error);
        self.library.save_download_snapshot(snapshot)
    }
}

fn transition_snapshot(
    snapshot: &mut DurableDownloadSnapshot,
    next: DownloadState,
) -> Result<(), CoreError> {
    if snapshot.state == next {
        return Ok(());
    }
    let mut machine = DownloadStateMachine::new(snapshot.state);
    machine.transition(next)?;
    snapshot.state = machine.state();
    Ok(())
}

fn total_expected_bytes(plan: &DownloadPlan) -> Option<u64> {
    let mut total = 0_u64;
    for asset in &plan.assets {
        total = total.checked_add(asset.expected_bytes?)?;
    }
    Some(total)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Compatibility, DownloadPlanAsset, MediaKind, QualityChoice, SourceIdentity};
    use std::sync::Arc;
    use std::thread;
    use tiny_http::{Response as TinyResponse, Server};

    struct FixtureServer {
        address: String,
        stop: Arc<AtomicBool>,
        handle: Option<thread::JoinHandle<()>>,
    }
    impl FixtureServer {
        fn start(body: Vec<u8>) -> Self {
            Self::start_with_delay(body, std::time::Duration::ZERO)
        }

        fn start_with_delay(body: Vec<u8>, response_delay: std::time::Duration) -> Self {
            let server = Server::http("127.0.0.1:0").unwrap();
            let address = format!("http://{}", server.server_addr());
            let stop = Arc::new(AtomicBool::new(false));
            let stop_thread = Arc::clone(&stop);
            let handle = thread::spawn(move || {
                while !stop_thread.load(Ordering::Relaxed) {
                    let Ok(Some(request)) =
                        server.recv_timeout(std::time::Duration::from_millis(50))
                    else {
                        continue;
                    };
                    thread::sleep(response_delay);
                    request
                        .respond(TinyResponse::from_data(body.clone()))
                        .unwrap();
                }
            });
            Self {
                address,
                stop,
                handle: Some(handle),
            }
        }
    }
    impl Drop for FixtureServer {
        fn drop(&mut self) {
            self.stop.store(true, Ordering::Relaxed);
            if let Some(handle) = self.handle.take() {
                handle.join().unwrap();
            }
        }
    }
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
    fn plan(job_id: &str, url: String, bytes: usize) -> DownloadWorkItem {
        DownloadWorkItem {
            job_id: job_id.into(),
            created_at_epoch_ms: 1_000,
            plan: DownloadPlan {
                source: SourceIdentity {
                    provider: "fixture".into(),
                    media_id: job_id.into(),
                    canonical_url: Some(format!("https://fixture.invalid/{job_id}")),
                },
                title: format!("Video {job_id}"),
                quality: QualityChoice {
                    choice_id: "fixture".into(),
                    label: "720p".into(),
                    estimated_bytes: Some(bytes as u64),
                    video_height: Some(720),
                    audio_only: false,
                    compatibility: Compatibility::Preferred,
                },
                assets: vec![DownloadPlanAsset {
                    asset_id: "combined".into(),
                    kind: MediaKind::Video,
                    url,
                    relative_path: format!("items/{job_id}/video.mp4"),
                    expected_bytes: Some(bytes as u64),
                    expected_sha256: None,
                    mime_type: Some("video/mp4".into()),
                }],
            },
        }
    }

    fn unknown_length_plan(job_id: &str, url: String) -> DownloadWorkItem {
        let mut item = plan(job_id, url, 0);
        item.plan.quality.estimated_bytes = None;
        item.plan.assets[0].expected_bytes = None;
        item
    }

    #[test]
    fn executes_real_transfer_promotes_item_and_honors_concurrency() {
        let data = b"worker fixture".repeat(32);
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("job-a", DownloadState::Queued))
            .unwrap();
        store
            .save_download_snapshot(&snapshot("job-b", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let report = worker
            .execute_ready_at(
                &[
                    plan("job-a", format!("{}/a.mp4", server.address), data.len()),
                    plan("job-b", format!("{}/b.mp4", server.address), data.len()),
                ],
                &AtomicBool::new(false),
                10_000,
            )
            .unwrap();
        assert_eq!(report.claimed, vec!["job-a"]);
        assert_eq!(report.completed, vec!["job-a"]);
        assert!(root.path().join("items/job-a/video.mp4").is_file());
        assert!(store.staged_job_ids().unwrap().is_empty());
        let item = store.get("job-a").unwrap().unwrap();
        assert!(item.completed);
        assert_eq!(item.assets[0].bytes, data.len() as u64);
        let jobs = store.load_download_snapshots().unwrap();
        assert_eq!(jobs[0].state, DownloadState::Completed);
        assert_eq!(jobs[0].bytes_downloaded, data.len() as u64);
        assert_eq!(jobs[1].state, DownloadState::Queued);
    }

    #[test]
    fn worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer() {
        let data = b"metric fixture".repeat(64);
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("metric-job", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

        let report = worker
            .execute_ready_at(
                &[plan(
                    "metric-job",
                    format!("{}/metric.mp4", server.address),
                    data.len(),
                )],
                &AtomicBool::new(false),
                50_000,
            )
            .unwrap();

        let CoreEvent::DownloadProgress {
            job_id,
            bytes_downloaded,
            total_bytes,
            metrics,
        } = report.progress_events.last().unwrap()
        else {
            panic!("worker emitted a non-progress event");
        };
        assert_eq!(job_id, "metric-job");
        assert_eq!(*bytes_downloaded, data.len() as u64);
        assert_eq!(*total_bytes, Some(data.len() as u64));
        assert_eq!(metrics.bytes_per_second, Some(data.len() as u64));
        assert_eq!(metrics.eta_seconds, Some(0));
    }

    #[test]
    fn worker_does_not_fabricate_eta_for_unknown_length_transfer() {
        let data = b"unknown metric fixture".repeat(32);
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("unknown-job", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

        let report = worker
            .execute_ready_at(
                &[unknown_length_plan(
                    "unknown-job",
                    format!("{}/unknown.mp4", server.address),
                )],
                &AtomicBool::new(false),
                60_000,
            )
            .unwrap();

        let CoreEvent::DownloadProgress {
            bytes_downloaded,
            total_bytes,
            metrics,
            ..
        } = report.progress_events.last().unwrap()
        else {
            panic!("worker emitted a non-progress event");
        };
        assert_eq!(*bytes_downloaded, data.len() as u64);
        assert_eq!(*total_bytes, None);
        assert_eq!(metrics.bytes_per_second, Some(data.len() as u64));
        assert_eq!(metrics.eta_seconds, None);
    }

    #[test]
    fn retryable_failure_enters_retry_wait_with_attempt_and_error() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("job-fail", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let policy = DownloadPolicy {
            max_attempts: 2,
            request_timeout: std::time::Duration::from_millis(100),
            ..DownloadPolicy::default()
        };
        let worker = DownloadWorker::new(store.clone(), root.path(), policy, 2);
        let report = worker
            .execute_ready_at(
                &[plan(
                    "job-fail",
                    "http://127.0.0.1:1/missing.mp4".into(),
                    10,
                )],
                &AtomicBool::new(false),
                5_000,
            )
            .unwrap();
        assert_eq!(report.retry_wait, vec!["job-fail"]);
        let snapshot = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(snapshot.state, DownloadState::RetryWait);
        assert_eq!(snapshot.attempt, 1);
        assert!(snapshot.retry_at_epoch_ms.unwrap() > 5_000);
        assert!(snapshot.last_error.unwrap().retryable);
    }

    #[test]
    fn interrupted_claims_are_repaired_after_process_death() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("resolving", DownloadState::Resolving))
            .unwrap();
        store
            .save_download_snapshot(&snapshot("downloading", DownloadState::Downloading))
            .unwrap();
        store
            .save_download_snapshot(&snapshot("queued", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 2);
        let report = worker.repair_interrupted_claims_at(42_000).unwrap();
        assert_eq!(report.repaired, vec!["downloading", "resolving"]);
        let snapshots = store.load_download_snapshots().unwrap();
        assert_eq!(
            snapshots
                .iter()
                .filter(|snapshot| snapshot.state == DownloadState::RetryWait)
                .count(),
            2
        );
        assert_eq!(
            snapshots
                .iter()
                .find(|snapshot| snapshot.job_id == "queued")
                .unwrap()
                .state,
            DownloadState::Queued
        );
    }

    #[test]
    fn durable_pause_is_not_reclassified_as_terminal_cancel() {
        let data = vec![7_u8; 4 * 1024 * 1024];
        let server =
            FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("pause-job", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let work = plan(
            "pause-job",
            format!("{}/pause.mp4", server.address),
            data.len(),
        );
        let control_store = store.clone();
        let controller = thread::spawn(move || {
            for _ in 0..100 {
                let mut current = control_store.load_download_snapshots().unwrap().remove(0);
                if current.state == DownloadState::Downloading {
                    let mut machine = DownloadStateMachine::new(current.state);
                    machine.transition(DownloadState::Paused).unwrap();
                    current.state = machine.state();
                    control_store.save_download_snapshot(&current).unwrap();
                    return;
                }
                thread::sleep(std::time::Duration::from_millis(5));
            }
            panic!("worker never entered downloading state");
        });
        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        controller.join().unwrap();
        assert_eq!(report.paused, vec!["pause-job"]);
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Paused);
        assert!(report.canceled.is_empty());
    }
}
