use super::*;

impl DownloadWorker {
    // A stop is authoritative only for the original claimed attempt.
    pub(super) fn durable_stop_for_claim(
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
    pub(super) fn settle_active_claim(
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

    pub(super) fn finish_platform_stopped(
        &self,
        claim: &mut DurableDownloadSnapshot,
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
            transition_snapshot(&mut next, DownloadState::RetryWait)?;
            next.retry_at_epoch_ms = Some(now_epoch_ms);
            next.last_error = Some(CoreError::new(
                ErrorKind::NetworkUnavailable,
                "Platform interrupted download; safe to retry",
                true,
            ));
            if self
                .library
                .replace_download_snapshot_if_unchanged(&current, &next)?
            {
                *claim = next;
                return Ok(true);
            }
        }
        Ok(false)
    }

    pub(super) fn finish_canceled(
        &self,
        claim: &mut DurableDownloadSnapshot,
    ) -> Result<bool, CoreError> {
        self.settle_active_claim(claim, DownloadState::Canceled, None, 0)
    }

    pub(super) fn finish_failed_or_retry(
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
