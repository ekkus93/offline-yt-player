use crate::{
    CoreError, CoreEvent, DownloadEngine, DownloadPlan, DownloadPolicy, DownloadState,
    DownloadStateMachine, DurableDownloadSnapshot, DurableStopReason, ErrorKind, LibraryItem,
    LibraryStore, LocalAsset, PAUSE_POLL_INTERVAL, ProgressCoalescer, TransferMetricEstimator,
    TransferRequest, bounded_download_concurrency, propagate_durable_stop, retry_delay,
};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Instant;

mod settlement;
#[cfg(test)]
mod tests;

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
        self.execute_ready_with_platform_stop_at(
            work_items,
            cancel,
            &AtomicBool::new(false),
            now_epoch_ms,
        )
    }

    /// A platform stop is not a user cancellation. Preserve the durable work
    /// and settle the original attempt as retryable, never as Canceled.
    pub fn execute_ready_with_platform_stop_at(
        &self,
        work_items: &[DownloadWorkItem],
        cancel: &AtomicBool,
        platform_stop: &AtomicBool,
        now_epoch_ms: u64,
    ) -> Result<DownloadWorkerReport, CoreError> {
        self.execute_ready_with_engine_and_platform_stop_at(
            work_items,
            cancel,
            platform_stop,
            now_epoch_ms,
            DownloadEngine::new(&self.library_root, self.policy.clone()),
        )
    }

    #[cfg(test)]
    fn execute_ready_with_engine_at(
        &self,
        work_items: &[DownloadWorkItem],
        cancel: &AtomicBool,
        now_epoch_ms: u64,
        engine: Result<DownloadEngine, CoreError>,
    ) -> Result<DownloadWorkerReport, CoreError> {
        self.execute_ready_with_engine_and_platform_stop_at(
            work_items,
            cancel,
            &AtomicBool::new(false),
            now_epoch_ms,
            engine,
        )
    }

    fn execute_ready_with_engine_and_platform_stop_at(
        &self,
        work_items: &[DownloadWorkItem],
        cancel: &AtomicBool,
        platform_stop: &AtomicBool,
        now_epoch_ms: u64,
        engine: Result<DownloadEngine, CoreError>,
    ) -> Result<DownloadWorkerReport, CoreError> {
        let engine = engine?;
        if platform_stop.load(Ordering::Acquire) {
            return Ok(DownloadWorkerReport::default());
        }
        let mut report = DownloadWorkerReport::default();
        let claimed = self.claim_eligible(work_items, now_epoch_ms)?;

        for (mut snapshot, item) in claimed {
            report.claimed.push(snapshot.job_id.clone());
            if platform_stop.load(Ordering::Acquire) {
                if self.finish_platform_stopped(&mut snapshot, now_epoch_ms)? {
                    report.retry_wait.push(snapshot.job_id.clone());
                }
                continue;
            }
            if cancel.load(Ordering::Relaxed) {
                if self.finish_canceled(&mut snapshot)? {
                    report.canceled.push(snapshot.job_id.clone());
                }
                continue;
            }
            match self.execute_one(
                &engine,
                item,
                &mut snapshot,
                cancel,
                platform_stop,
                now_epoch_ms,
            ) {
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
                        None if platform_stop.load(Ordering::Acquire) => {
                            if self.finish_platform_stopped(&mut snapshot, now_epoch_ms)? {
                                report.retry_wait.push(snapshot.job_id.clone());
                            }
                        }
                        None => {
                            if self.finish_canceled(&mut snapshot)? {
                                report.canceled.push(snapshot.job_id.clone());
                            }
                        }
                    }
                }
                Err(error) => {
                    if platform_stop.load(Ordering::Acquire) {
                        if self.finish_platform_stopped(&mut snapshot, now_epoch_ms)? {
                            report.retry_wait.push(snapshot.job_id.clone());
                        }
                        continue;
                    }
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
        platform_stop: &AtomicBool,
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
            if !output_paths.insert(normalized_path) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "duplicate download plan asset path",
                    false,
                ));
            }
        }
        if platform_stop.load(Ordering::Acquire) {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "platform stopped download",
                false,
            ));
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
            if cancel.load(Ordering::Relaxed) || platform_stop.load(Ordering::Acquire) {
                return Err(CoreError::new(
                    ErrorKind::Canceled,
                    "Download execution stopped",
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
                platform_stop,
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

        if platform_stop.load(Ordering::Acquire) {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "platform stopped before verification",
                false,
            ));
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
        if platform_stop.load(Ordering::Acquire) {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "platform stopped before completion",
                false,
            ));
        }
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
        platform_stop: &AtomicBool,
        on_progress: impl FnMut(u64) -> Result<(), CoreError>,
    ) -> Result<crate::TransferResult, CoreError> {
        let transfer_stop = AtomicBool::new(false);
        let polling_done = AtomicBool::new(false);
        let poll_error = std::sync::Mutex::new(None);

        let result = std::thread::scope(|scope| {
            scope.spawn(|| {
                while !polling_done.load(Ordering::Acquire) {
                    if external_cancel.load(Ordering::Acquire)
                        || platform_stop.load(Ordering::Acquire)
                    {
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
