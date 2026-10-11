use crate::{
    CoreError, CoreEvent, DownloadEngine, DownloadPlan, DownloadPolicy, DownloadState,
    DownloadStateMachine, DurableDownloadSnapshot, DurableStopReason, ErrorKind, LibraryItem,
    LibraryStore, LocalAsset, PAUSE_POLL_INTERVAL, ProgressCoalescer, TransferMetricEstimator,
    TransferRequest, bounded_download_concurrency, propagate_durable_stop, retry_delay,
};
use sha2::{Digest, Sha256};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Instant;

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
        // Build the fallible runtime before claiming durable jobs. Otherwise a
        // client initialization failure leaves jobs stuck in Resolving.
        self.execute_ready_with_engine_at(
            work_items,
            cancel,
            now_epoch_ms,
            DownloadEngine::new(&self.library_root, self.policy.clone()),
        )
    }

    fn execute_ready_with_engine_at(
        &self,
        work_items: &[DownloadWorkItem],
        cancel: &AtomicBool,
        now_epoch_ms: u64,
        engine: Result<DownloadEngine, CoreError>,
    ) -> Result<DownloadWorkerReport, CoreError> {
        let engine = engine?;
        let mut report = DownloadWorkerReport::default();
        let claimed = self.claim_eligible(work_items, now_epoch_ms)?;

        for (mut snapshot, item) in claimed {
            report.claimed.push(snapshot.job_id.clone());
            if cancel.load(Ordering::Relaxed) {
                if self.finish_canceled(&mut snapshot)? {
                    report.canceled.push(snapshot.job_id.clone());
                }
                continue;
            }
            match self.execute_one(&engine, item, &mut snapshot, cancel, now_epoch_ms) {
                Ok(progress_events) => {
                    report.progress_events.extend(progress_events);
                    report.completed.push(snapshot.job_id.clone());
                }
                Err(error) if error.kind == ErrorKind::Canceled => {
                    match self.durable_stop_for_claim(&snapshot)? {
                        Some(DurableStopReason::Pause) => {
                            report.paused.push(snapshot.job_id.clone())
                        }
                        Some(DurableStopReason::Cancel) => {
                            report.canceled.push(snapshot.job_id.clone())
                        }
                        None => {
                            if self.finish_canceled(&mut snapshot)? {
                                report.canceled.push(snapshot.job_id.clone());
                            }
                        }
                    }
                }
                Err(error) => {
                    let retryable = error.retryable && snapshot.attempt < self.policy.max_attempts;
                    if self.finish_failed_or_retry(&mut snapshot, error, retryable, now_epoch_ms)? {
                        if retryable {
                            report.retry_wait.push(snapshot.job_id.clone());
                        } else {
                            report.failed.push(snapshot.job_id.clone());
                        }
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
                let previous = snapshot.clone();
                transition_snapshot(&mut snapshot, DownloadState::RetryWait)?;
                snapshot.retry_at_epoch_ms = Some(now_epoch_ms);
                snapshot.last_error = Some(CoreError::new(
                    ErrorKind::NetworkUnavailable,
                    "Download was interrupted before completion",
                    true,
                ));
                // A concurrent worker may have completed, paused, or canceled
                // this row since the recovery scan. Never resurrect stale work.
                if self
                    .library
                    .replace_download_snapshot_if_unchanged(&previous, &snapshot)?
                {
                    report.repaired.push(snapshot.job_id);
                }
            }
        }
        Ok(report)
    }

    fn claim_eligible<'a>(
        &self,
        work_items: &'a [DownloadWorkItem],
        now_epoch_ms: u64,
    ) -> Result<Vec<(DurableDownloadSnapshot, &'a DownloadWorkItem)>, CoreError> {
        // Reject ambiguous work identities before claiming or mutating any durable queue rows.
        // Collecting directly into a HashMap silently picks the last conflicting plan.
        let mut by_id: HashMap<&str, &DownloadWorkItem> = HashMap::with_capacity(work_items.len());
        for item in work_items {
            if item.job_id.trim().is_empty() {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "download work job id must not be empty",
                    false,
                ));
            }
            if by_id.insert(item.job_id.as_str(), item).is_some() {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "duplicate download work job id",
                    false,
                ));
            }
        }
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
            let previous = snapshot.clone();
            transition_snapshot(&mut snapshot, DownloadState::Resolving)?;
            snapshot.attempt = snapshot.attempt.saturating_add(1);
            snapshot.last_error = None;
            // A second Android runtime may have claimed or paused this row
            // since our read; only the first exact-state claimant may execute.
            if self
                .library
                .replace_download_snapshot_if_unchanged(&previous, &snapshot)?
            {
                claimed.push((snapshot, item));
            }
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
        if !item.plan.assets.iter().any(|asset| {
            matches!(
                asset.kind,
                crate::MediaKind::Video | crate::MediaKind::Audio
            )
        }) {
            return Err(CoreError::new(
                ErrorKind::NoCompatibleFormat,
                "download plan contains no playable media assets",
                false,
            ));
        }
        // Fail closed before any transfer or staging when asset identities collide.
        // Path components normalize aliases such as repeated separators and "./".
        let mut asset_ids = HashSet::new();
        let mut output_paths = HashSet::new();
        // Production URL jobs use a SHA-256-derived private directory. A
        // persisted/malformed plan must not write into another job's assets.
        let expected_url_job_root = if crate::security::validate_http_url(&item.job_id).is_ok() {
            let digest = Sha256::digest(item.job_id.as_bytes());
            Some(PathBuf::from("items").join(format!("by-job-{digest:x}")))
        } else {
            None
        };
        for asset in &item.plan.assets {
            if asset.asset_id.trim().is_empty() {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "download plan asset id must not be empty",
                    false,
                ));
            }
            if !asset_ids.insert(asset.asset_id.as_str()) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "duplicate download plan asset id",
                    false,
                ));
            }
            if asset.relative_path.trim().is_empty() {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "download plan asset path must not be empty",
                    false,
                ));
            }
            crate::security::validate_relative_library_path(&asset.relative_path)?;
            let normalized_path: PathBuf = std::path::Path::new(&asset.relative_path)
                .components()
                .filter_map(|component| match component {
                    std::path::Component::Normal(name) => Some(name),
                    _ => None,
                })
                .collect();
            if normalized_path.as_os_str().is_empty() {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "download plan asset path must contain a filename",
                    false,
                ));
            }
            if expected_url_job_root.as_ref().is_some_and(|root| {
                !normalized_path.starts_with(root) || normalized_path.as_path() == root.as_path()
            }) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "download plan asset path is outside the claimed job directory",
                    false,
                ));
            }
            if !output_paths.insert(normalized_path) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "duplicate download plan asset path",
                    false,
                ));
            }
        }
        let prior_claim = snapshot.clone();
        transition_snapshot(snapshot, DownloadState::Downloading)?;
        snapshot.total_bytes = total_expected_bytes(&item.plan);
        // Retry replays assets; transfer results report full sizes, not byte deltas.
        snapshot.bytes_downloaded = 0;
        // Pause/cancel can race the period between claim and execution startup.
        // A stale worker must not change that durable decision back to Downloading.
        if !self
            .library
            .replace_download_snapshot_if_unchanged(&prior_claim, snapshot)?
        {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "download execution was superseded by a concurrent queue update",
                false,
            ));
        }

        let mut assets = Vec::new();
        let mut coalescer = ProgressCoalescer::default();
        let mut metric_estimator = TransferMetricEstimator::default();
        // Record transfer time from a monotonic clock, not synthetic fixed
        // one-second steps per asset. Speed/ETA must reflect real elapsed time.
        let transfer_started = Instant::now();
        let mut progress_events = Vec::new();
        metric_estimator.observe(
            now_epoch_ms,
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
            let bytes_before_asset = snapshot.bytes_downloaded;
            let mut last_persisted = snapshot.clone();
            let result = self.transfer_with_durable_stop(
                engine,
                &item.job_id,
                &request,
                cancel,
                |written| {
                    snapshot.bytes_downloaded = bytes_before_asset.saturating_add(written);
                    let elapsed_ms =
                        u64::try_from(transfer_started.elapsed().as_millis()).unwrap_or(u64::MAX);
                    let tick_ms = now_epoch_ms.saturating_add(elapsed_ms);
                    let metrics = metric_estimator.observe(
                        tick_ms,
                        snapshot.bytes_downloaded,
                        snapshot.total_bytes,
                    );
                    if coalescer.should_emit(tick_ms, snapshot.bytes_downloaded, false) {
                        if !self
                            .library
                            .replace_download_snapshot_if_unchanged(&last_persisted, snapshot)?
                        {
                            return Err(CoreError::new(
                                ErrorKind::Canceled,
                                "download was superseded during transfer progress",
                                false,
                            ));
                        }
                        last_persisted = snapshot.clone();
                        progress_events.push(CoreEvent::DownloadProgress {
                            job_id: snapshot.job_id.clone(),
                            bytes_downloaded: snapshot.bytes_downloaded,
                            total_bytes: snapshot.total_bytes,
                            metrics,
                        });
                    }
                    Ok(())
                },
            )?;
            snapshot.bytes_downloaded = bytes_before_asset.saturating_add(result.bytes);
            // A short transfer may not reach the coalescer interval/byte threshold.
            // Persist its final bytes and emit the final event before asset staging.
            if last_persisted.bytes_downloaded != snapshot.bytes_downloaded {
                let elapsed_ms =
                    u64::try_from(transfer_started.elapsed().as_millis()).unwrap_or(u64::MAX);
                let tick_ms = now_epoch_ms.saturating_add(elapsed_ms);
                let metrics = metric_estimator.observe(
                    tick_ms,
                    snapshot.bytes_downloaded,
                    snapshot.total_bytes,
                );
                if !self
                    .library
                    .replace_download_snapshot_if_unchanged(&last_persisted, snapshot)?
                {
                    return Err(CoreError::new(
                        ErrorKind::Canceled,
                        "download was superseded before asset staging",
                        false,
                    ));
                }
                coalescer.should_emit(tick_ms, snapshot.bytes_downloaded, true);
                progress_events.push(CoreEvent::DownloadProgress {
                    job_id: snapshot.job_id.clone(),
                    bytes_downloaded: snapshot.bytes_downloaded,
                    total_bytes: snapshot.total_bytes,
                    metrics,
                });
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

        let previous_downloading = snapshot.clone();
        transition_snapshot(snapshot, DownloadState::Verifying)?;
        if !self
            .library
            .replace_download_snapshot_if_unchanged(&previous_downloading, snapshot)?
        {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "download was superseded before verification",
                false,
            ));
        }
        let library_item = LibraryItem {
            item_id: item.job_id.clone(),
            source: item.plan.source.clone(),
            display_title: item.plan.title.clone(),
            duration_ms: item.plan.duration_ms,
            quality_label: item.plan.quality.label.clone(),
            assets,
            created_at_epoch_ms: item.created_at_epoch_ms,
            playback_position_ms: 0,
            completed: true,
        };
        let previous_verifying = snapshot.clone();
        transition_snapshot(snapshot, DownloadState::Completed)?;
        snapshot.bytes_downloaded = snapshot.total_bytes.unwrap_or(snapshot.bytes_downloaded);
        snapshot.retry_at_epoch_ms = None;
        snapshot.last_error = None;
        if !self.library.promote_completed_if_unchanged(
            &item.job_id,
            &library_item,
            &previous_verifying,
            snapshot,
        )? {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "download was superseded before Library completion",
                false,
            ));
        }
        Ok(progress_events)
    }

    fn transfer_with_durable_stop(
        &self,
        engine: &DownloadEngine,
        job_id: &str,
        request: &TransferRequest,
        external_cancel: &AtomicBool,
        on_progress: impl FnMut(u64) -> Result<(), CoreError>,
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
            let transfer_result =
                engine.transfer_with_progress(request, &transfer_stop, on_progress);
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

    // A stop is authoritative only for the original claimed attempt.
    fn durable_stop_for_claim(
        &self,
        claim: &DurableDownloadSnapshot,
    ) -> Result<Option<DurableStopReason>, CoreError> {
        let row = self
            .library
            .load_download_snapshots()?
            .into_iter()
            .find(|row| row.job_id == claim.job_id);
        Ok(match row {
            Some(row) if row.attempt == claim.attempt => match row.state {
                DownloadState::Paused => Some(DurableStopReason::Pause),
                DownloadState::Canceled => Some(DurableStopReason::Cancel),
                _ => None,
            },
            _ => None,
        })
    }

    // Reload before every CAS attempt so a concurrent pause, cancellation,
    // reclaimed claim, or newly persisted progress is never lost.
    fn settle_active_claim(
        &self,
        claim: &mut DurableDownloadSnapshot,
        next_state: DownloadState,
        error: Option<CoreError>,
        now_epoch_ms: u64,
    ) -> Result<bool, CoreError> {
        for _ in 0..16 {
            let Some(current) = self
                .library
                .load_download_snapshots()?
                .into_iter()
                .find(|row| row.job_id == claim.job_id)
            else {
                return Ok(false);
            };
            if current.attempt != claim.attempt
                || !matches!(
                    current.state,
                    DownloadState::Resolving
                        | DownloadState::Downloading
                        | DownloadState::Verifying
                )
            {
                return Ok(false);
            }
            let mut next = current.clone();
            transition_snapshot(&mut next, next_state)?;
            next.retry_at_epoch_ms = if next_state == DownloadState::RetryWait {
                let delay_ms =
                    u64::try_from(retry_delay(current.attempt, 0).as_millis()).unwrap_or(u64::MAX);
                Some(now_epoch_ms.saturating_add(delay_ms))
            } else {
                None
            };
            next.last_error = error.clone();
            if self
                .library
                .replace_download_snapshot_if_unchanged(&current, &next)?
            {
                *claim = next;
                return Ok(true);
            }
        }
        // Contention alone never constitutes a successful outcome.
        Ok(false)
    }

    fn finish_canceled(&self, claim: &mut DurableDownloadSnapshot) -> Result<bool, CoreError> {
        self.settle_active_claim(claim, DownloadState::Canceled, None, 0)
    }

    fn finish_failed_or_retry(
        &self,
        claim: &mut DurableDownloadSnapshot,
        error: CoreError,
        retryable: bool,
        now_epoch_ms: u64,
    ) -> Result<bool, CoreError> {
        self.settle_active_claim(
            claim,
            if retryable {
                DownloadState::RetryWait
            } else {
                DownloadState::Failed
            },
            Some(error),
            now_epoch_ms,
        )
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
                duration_ms: Some(60_000),
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
    fn stale_error_and_cancel_handlers_preserve_user_pause_and_cancel() {
        for state in [DownloadState::Paused, DownloadState::Canceled] {
            let store = LibraryStore::open_in_memory().unwrap();
            let root = tempfile::tempdir().unwrap();
            let worker =
                DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
            let mut claim = snapshot("stale-stop", DownloadState::Downloading);
            claim.attempt = 2;
            store.save_download_snapshot(&claim).unwrap();
            let mut updated = claim.clone();
            updated.state = state;
            updated.bytes_downloaded = 81;
            store.save_download_snapshot(&updated).unwrap();
            assert!(
                !worker
                    .finish_failed_or_retry(
                        &mut claim,
                        CoreError::new(ErrorKind::NetworkTimeout, "old failure", true),
                        true,
                        42,
                    )
                    .unwrap()
            );
            assert!(!worker.finish_canceled(&mut claim).unwrap());
            assert_eq!(store.load_download_snapshots().unwrap(), vec![updated]);
            assert!(worker.durable_stop_for_claim(&claim).unwrap().is_some());
        }
    }

    #[test]
    fn reclaimed_attempt_cannot_receive_stale_settlement_or_stop_report() {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut claim = snapshot("reclaimed", DownloadState::Downloading);
        claim.attempt = 1;
        store.save_download_snapshot(&claim).unwrap();
        let mut newer = claim.clone();
        newer.attempt = 2;
        newer.bytes_downloaded = 123;
        store.save_download_snapshot(&newer).unwrap();
        assert!(
            !worker
                .finish_failed_or_retry(
                    &mut claim,
                    CoreError::new(ErrorKind::NetworkUnavailable, "old", false),
                    false,
                    30,
                )
                .unwrap()
        );
        assert!(!worker.finish_canceled(&mut claim).unwrap());
        assert!(worker.durable_stop_for_claim(&claim).unwrap().is_none());
        assert_eq!(
            store.load_download_snapshots().unwrap(),
            vec![newer.clone()]
        );
        newer.state = DownloadState::Paused;
        store.save_download_snapshot(&newer).unwrap();
        assert!(worker.durable_stop_for_claim(&claim).unwrap().is_none());
    }

    #[test]
    fn settling_failure_or_cancel_keeps_latest_progress_and_total_bytes() {
        for outcome in [
            DownloadState::Failed,
            DownloadState::RetryWait,
            DownloadState::Canceled,
        ] {
            let store = LibraryStore::open_in_memory().unwrap();
            let root = tempfile::tempdir().unwrap();
            let worker =
                DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
            let mut claim = snapshot("progress", DownloadState::Downloading);
            claim.attempt = 3;
            claim.bytes_downloaded = 5;
            store.save_download_snapshot(&claim).unwrap();
            let mut persisted = claim.clone();
            persisted.bytes_downloaded = 49;
            persisted.total_bytes = Some(128);
            store.save_download_snapshot(&persisted).unwrap();
            let success = if outcome == DownloadState::Canceled {
                worker.finish_canceled(&mut claim).unwrap()
            } else {
                worker
                    .finish_failed_or_retry(
                        &mut claim,
                        CoreError::new(ErrorKind::NetworkTimeout, "fixture", true),
                        outcome == DownloadState::RetryWait,
                        20_000,
                    )
                    .unwrap()
            };
            assert!(success);
            let durable = store.load_download_snapshots().unwrap().remove(0);
            assert_eq!(durable.state, outcome);
            assert_eq!(durable.bytes_downloaded, 49);
            assert_eq!(durable.total_bytes, Some(128));
            assert_eq!(durable.attempt, 3);
            assert_eq!(durable, claim);
        }
    }

    #[test]
    fn engine_initialization_failure_does_not_claim_durable_work() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("init-failure", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let work = plan("init-failure", "http://127.0.0.1/unused".into(), 16);

        // Inject the fallible engine-construction outcome without depending on
        // process-global proxy variables or ambient network configuration.
        let failure = CoreError::new(
            ErrorKind::NetworkUnavailable,
            "synthetic HTTP client initialization failure",
            true,
        );
        let error = worker
            .execute_ready_with_engine_at(&[work], &AtomicBool::new(false), 10_000, Err(failure))
            .unwrap_err();

        assert_eq!(error.kind, ErrorKind::NetworkUnavailable);
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Queued);
        assert_eq!(durable.attempt, 0);
        assert_eq!(durable.retry_at_epoch_ms, None);
        assert!(durable.last_error.is_none());
        assert!(store.get("init-failure").unwrap().is_none());
    }

    #[test]
    fn pause_after_claim_prevents_stale_worker_from_starting_transfer() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut claimed = snapshot("pause-after-claim", DownloadState::Resolving);
        claimed.attempt = 1;
        store.save_download_snapshot(&claimed).unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut paused = claimed.clone();
        paused.state = DownloadState::Paused;
        store.save_download_snapshot(&paused).unwrap();
        let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
        let item = plan(
            "pause-after-claim",
            "http://127.0.0.1:1/no-transfer".into(),
            64,
        );
        let error = worker
            .execute_one(
                &engine,
                &item,
                &mut claimed,
                &AtomicBool::new(false),
                10_000,
            )
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
        assert_eq!(store.load_download_snapshots().unwrap(), vec![paused]);
        assert!(store.staged_job_ids().unwrap().is_empty());
        assert!(store.get("pause-after-claim").unwrap().is_none());
        assert!(
            !root
                .path()
                .join("items/pause-after-claim/video.mp4")
                .exists()
        );
    }

    #[test]
    fn empty_plan_fails_without_promoting_an_unplayable_library_item() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("empty-plan", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut work = plan("empty-plan", "http://127.0.0.1/unused".into(), 0);
        work.plan.assets.clear();

        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        assert_eq!(report.failed, vec!["empty-plan"]);
        assert!(report.completed.is_empty());
        assert!(store.get("empty-plan").unwrap().is_none());
        let snapshot = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(snapshot.state, DownloadState::Failed);
        assert_eq!(snapshot.bytes_downloaded, 0);
        assert_eq!(
            snapshot.last_error.unwrap().kind,
            ErrorKind::NoCompatibleFormat
        );
    }

    #[test]
    fn thumbnail_only_plan_cannot_complete_as_playable_media() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("thumbnail-only", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut work = plan("thumbnail-only", "http://127.0.0.1/unused".into(), 100);
        work.plan.assets[0].kind = MediaKind::Thumbnail;

        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        assert_eq!(report.failed, vec!["thumbnail-only"]);
        assert!(store.get("thumbnail-only").unwrap().is_none());
        let snapshot = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(snapshot.state, DownloadState::Failed);
        assert_eq!(
            snapshot.last_error.unwrap().kind,
            ErrorKind::NoCompatibleFormat
        );
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
        assert_eq!(item.duration_ms, Some(60_000));
        assert_eq!(item.assets[0].bytes, data.len() as u64);
        let jobs = store.load_download_snapshots().unwrap();
        assert_eq!(jobs[0].state, DownloadState::Completed);
        assert_eq!(jobs[0].bytes_downloaded, data.len() as u64);
        assert_eq!(jobs[1].state, DownloadState::Queued);
    }

    #[test]
    fn worker_persists_chunk_progress_before_single_asset_completion() {
        let data = vec![0x42; 256 * 1024];
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("chunk-progress", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let report = worker
            .execute_ready_at(
                &[plan(
                    "chunk-progress",
                    format!("{}/chunk.mp4", server.address),
                    data.len(),
                )],
                &AtomicBool::new(false),
                10_000,
            )
            .unwrap();
        assert_eq!(report.completed, vec!["chunk-progress"]);
        assert!(report.progress_events.iter().any(|event| {
            matches!(
                event,
                CoreEvent::DownloadProgress { bytes_downloaded, .. }
                    if *bytes_downloaded > 0 && *bytes_downloaded < data.len() as u64
            )
        }));
        assert_eq!(
            store.load_download_snapshots().unwrap()[0].bytes_downloaded,
            data.len() as u64
        );
    }

    #[test]
    fn worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer() {
        let data = b"metric fixture".repeat(64);
        let server =
            FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
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
        // With a real network delay, measured speed is positive but does not
        // pretend that each transfer took precisely one synthetic second.
        assert!(metrics.bytes_per_second.is_some_and(|speed| speed > 0));
        assert_eq!(metrics.eta_seconds, Some(0));
    }

    #[test]
    fn worker_does_not_fabricate_eta_for_unknown_length_transfer() {
        let data = b"unknown metric fixture".repeat(32);
        let server =
            FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
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
        assert!(metrics.bytes_per_second.is_some_and(|speed| speed > 0));
        assert_eq!(metrics.eta_seconds, None);
    }

    #[test]
    fn duplicate_work_ids_fail_before_any_durable_claim_or_transfer() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("duplicate-job", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let first = plan("duplicate-job", "http://127.0.0.1:1/first".into(), 5);
        let second = plan("duplicate-job", "http://127.0.0.1:1/second".into(), 5);

        let error = worker
            .execute_ready_at(&[first, second], &AtomicBool::new(false), 10_000)
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Queued);
        assert_eq!(durable.attempt, 0);
        assert!(store.get("duplicate-job").unwrap().is_none());
        assert!(store.staged_job_ids().unwrap().is_empty());
    }

    #[test]
    fn blank_work_id_fails_before_claiming_other_valid_work() {
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot("valid-job", DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let valid = plan("valid-job", "http://127.0.0.1:1/valid".into(), 5);
        let blank = plan("   ", "http://127.0.0.1:1/blank".into(), 5);

        let error = worker
            .execute_ready_at(&[valid, blank], &AtomicBool::new(false), 10_000)
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Queued);
        assert_eq!(durable.attempt, 0);
    }

    #[test]
    fn url_job_rejects_cross_job_asset_path_before_transfer_or_staging() {
        let job_id = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        let other_id = "https://www.youtube.com/watch?v=abcdefghijk";
        let other_digest = Sha256::digest(other_id.as_bytes());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot(job_id, DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut work = plan(job_id, "http://127.0.0.1:1/unreachable".into(), 10);
        work.plan.assets[0].relative_path =
            format!("items/by-job-{other_digest:x}/video.mp4");

        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        assert_eq!(report.failed, vec![job_id]);
        assert!(report.completed.is_empty());
        assert!(store.get(job_id).unwrap().is_none());
        assert!(store.staged_job_ids().unwrap().is_empty());
        assert!(!root
            .path()
            .join(format!("items/by-job-{other_digest:x}/video.mp4"))
            .exists());
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Failed);
        assert_eq!(durable.last_error.unwrap().kind, ErrorKind::InvalidInput);
    }

    #[test]
    fn url_job_accepts_its_own_hashed_asset_directory() {
        let job_id = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        let digest = Sha256::digest(job_id.as_bytes());
        let data = b"owned media fixture".repeat(32);
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot(job_id, DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut work = plan(
            job_id,
            format!("{}/owned.mp4", server.address),
            data.len(),
        );
        work.plan.assets[0].relative_path = format!("items/by-job-{digest:x}/video.mp4");

        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        assert_eq!(report.completed, vec![job_id]);
        assert!(root
            .path()
            .join(format!("items/by-job-{digest:x}/video.mp4"))
            .is_file());
    }

    #[test]
    fn invalid_asset_identities_fail_before_network_transfer_or_staging() {
        for case in [
            "duplicate-id",
            "duplicate-path",
            "normalized-path",
            "blank-id",
            "blank-path",
        ] {
            let job_id = format!("guard-{case}");
            let store = LibraryStore::open_in_memory().unwrap();
            store
                .save_download_snapshot(&snapshot(&job_id, DownloadState::Queued))
                .unwrap();
            let root = tempfile::tempdir().unwrap();
            let worker =
                DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
            let mut work = plan(&job_id, "http://127.0.0.1:1/unreachable".into(), 10);
            let mut second = work.plan.assets[0].clone();
            second.asset_id = "secondary".into();
            second.relative_path = format!("items/{job_id}/second.mp4");
            match case {
                "duplicate-id" => second.asset_id = "combined".into(),
                "duplicate-path" => {
                    second.relative_path = work.plan.assets[0].relative_path.clone()
                }
                "normalized-path" => {
                    second.relative_path = format!("items/{job_id}/./video.mp4");
                }
                "blank-id" => second.asset_id = "   ".into(),
                "blank-path" => second.relative_path = "  ".into(),
                _ => unreachable!(),
            }
            work.plan.assets.push(second);

            let report = worker
                .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
                .unwrap();
            assert_eq!(report.failed, vec![job_id.clone()], "{case}");
            assert!(report.completed.is_empty(), "{case}");
            assert!(report.retry_wait.is_empty(), "{case}");
            assert!(store.get(&job_id).unwrap().is_none(), "{case}");
            assert!(store.staged_job_ids().unwrap().is_empty(), "{case}");
            assert!(
                !root
                    .path()
                    .join(format!("items/{job_id}/video.mp4"))
                    .exists()
            );
            let durable = store.load_download_snapshots().unwrap().remove(0);
            assert_eq!(durable.state, DownloadState::Failed, "{case}");
            assert_eq!(durable.attempt, 1, "{case}");
            assert_eq!(durable.bytes_downloaded, 0, "{case}");
            assert_eq!(
                durable.last_error.unwrap().kind,
                ErrorKind::InvalidInput,
                "{case}"
            );
        }
    }

    #[test]
    fn retry_replayed_asset_does_not_double_count_prior_progress() {
        let data = b"retry fixture".repeat(64);
        let server = FixtureServer::start(data.clone());
        let store = LibraryStore::open_in_memory().unwrap();
        let mut prior = snapshot("retry-progress", DownloadState::RetryWait);
        prior.bytes_downloaded = data.len() as u64;
        prior.total_bytes = Some(data.len() as u64);
        prior.attempt = 1;
        prior.retry_at_epoch_ms = Some(10_000);
        store.save_download_snapshot(&prior).unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

        let report = worker
            .execute_ready_at(
                &[plan(
                    "retry-progress",
                    format!("{}/retry.mp4", server.address),
                    data.len(),
                )],
                &AtomicBool::new(false),
                10_000,
            )
            .unwrap();
        assert_eq!(report.completed, vec!["retry-progress"]);
        let completed = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(completed.state, DownloadState::Completed);
        assert_eq!(completed.attempt, 2);
        assert_eq!(completed.bytes_downloaded, data.len() as u64);
        let CoreEvent::DownloadProgress {
            bytes_downloaded,
            total_bytes,
            ..
        } = report.progress_events.last().unwrap()
        else {
            panic!("expected progress event");
        };
        assert_eq!(*bytes_downloaded, data.len() as u64);
        assert_eq!(*total_bytes, Some(data.len() as u64));
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
