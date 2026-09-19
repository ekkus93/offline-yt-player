use crate::domain::CoreError;
use crate::state::DownloadState;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;

/// Durable state can reconstruct the queue after process death.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DurableDownloadSnapshot {
    pub job_id: String,
    pub state: DownloadState,
    pub bytes_downloaded: u64,
    pub total_bytes: Option<u64>,
    pub attempt: u32,
    pub retry_at_epoch_ms: Option<u64>,
    pub last_error: Option<CoreError>,
}

/// Live metrics are ephemeral because transfer speed and ETA become stale after a restart.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EphemeralTransferMetrics {
    pub bytes_per_second: Option<u64>,
    pub eta_seconds: Option<u64>,
}

impl EphemeralTransferMetrics {
    #[must_use]
    pub const fn unknown() -> Self {
        Self {
            bytes_per_second: None,
            eta_seconds: None,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct ProgressSample {
    epoch_ms: u64,
    bytes_downloaded: u64,
}

/// Calculates speed from a bounded recent sample window and only reports ETA when total length is
/// known and enough progress has been observed. Unknown-length transfers never receive fabricated
/// ETA/progress values.
#[derive(Debug, Clone)]
pub struct TransferMetricEstimator {
    window_ms: u64,
    samples: VecDeque<ProgressSample>,
}

impl TransferMetricEstimator {
    #[must_use]
    pub fn new(window_ms: u64) -> Self {
        Self {
            window_ms: window_ms.max(1),
            samples: VecDeque::new(),
        }
    }

    pub fn observe(
        &mut self,
        now_epoch_ms: u64,
        bytes_downloaded: u64,
        total_bytes: Option<u64>,
    ) -> EphemeralTransferMetrics {
        self.samples.push_back(ProgressSample {
            epoch_ms: now_epoch_ms,
            bytes_downloaded,
        });
        self.prune(now_epoch_ms);
        self.metrics(bytes_downloaded, total_bytes)
    }

    fn prune(&mut self, now_epoch_ms: u64) {
        while self.samples.len() > 2
            && self
                .samples
                .front()
                .is_some_and(|sample| now_epoch_ms.saturating_sub(sample.epoch_ms) > self.window_ms)
        {
            self.samples.pop_front();
        }
    }

    fn metrics(&self, bytes_downloaded: u64, total_bytes: Option<u64>) -> EphemeralTransferMetrics {
        let Some(first) = self.samples.front() else {
            return EphemeralTransferMetrics::unknown();
        };
        let Some(last) = self.samples.back() else {
            return EphemeralTransferMetrics::unknown();
        };
        let elapsed_ms = last.epoch_ms.saturating_sub(first.epoch_ms);
        let bytes_delta = last.bytes_downloaded.saturating_sub(first.bytes_downloaded);
        if elapsed_ms == 0 || bytes_delta == 0 {
            return EphemeralTransferMetrics::unknown();
        }
        let bytes_per_second = bytes_delta.saturating_mul(1_000) / elapsed_ms;
        if bytes_per_second == 0 {
            return EphemeralTransferMetrics::unknown();
        }
        let eta_seconds = total_bytes.and_then(|total| {
            if bytes_downloaded >= total {
                Some(0)
            } else {
                let remaining = total.saturating_sub(bytes_downloaded);
                Some(remaining.div_ceil(bytes_per_second))
            }
        });
        EphemeralTransferMetrics {
            bytes_per_second: Some(bytes_per_second),
            eta_seconds,
        }
    }
}

impl Default for TransferMetricEstimator {
    fn default() -> Self {
        Self::new(5_000)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum CoreEvent {
    DownloadStateChanged(DurableDownloadSnapshot),
    DownloadProgress {
        job_id: String,
        bytes_downloaded: u64,
        total_bytes: Option<u64>,
        metrics: EphemeralTransferMetrics,
    },
    LibraryChanged,
}

/// Coalesces progress so each network chunk does not cross the FFI/UI boundary.
#[derive(Debug, Clone)]
pub struct ProgressCoalescer {
    min_interval_ms: u64,
    min_byte_delta: u64,
    last_emit_epoch_ms: Option<u64>,
    last_emit_bytes: u64,
}

impl ProgressCoalescer {
    #[must_use]
    pub const fn new(min_interval_ms: u64, min_byte_delta: u64) -> Self {
        Self {
            min_interval_ms,
            min_byte_delta,
            last_emit_epoch_ms: None,
            last_emit_bytes: 0,
        }
    }

    pub fn should_emit(
        &mut self,
        now_epoch_ms: u64,
        bytes_downloaded: u64,
        finished: bool,
    ) -> bool {
        let first = self.last_emit_epoch_ms.is_none();
        let interval_elapsed = self
            .last_emit_epoch_ms
            .is_some_and(|last| now_epoch_ms.saturating_sub(last) >= self.min_interval_ms);
        let bytes_advanced =
            bytes_downloaded.saturating_sub(self.last_emit_bytes) >= self.min_byte_delta;
        let emit = first || finished || (interval_elapsed && bytes_advanced);
        if emit {
            self.last_emit_epoch_ms = Some(now_epoch_ms);
            self.last_emit_bytes = bytes_downloaded;
        }
        emit
    }
}

impl Default for ProgressCoalescer {
    fn default() -> Self {
        Self::new(250, 256 * 1024)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn progress_is_coalesced() {
        let mut c = ProgressCoalescer::default();
        assert!(c.should_emit(1_000, 0, false));
        assert!(!c.should_emit(1_100, 300_000, false));
        assert!(c.should_emit(1_300, 300_000, false));
        assert!(!c.should_emit(1_700, 300_010, false));
        assert!(c.should_emit(1_701, 300_010, true));
    }

    #[test]
    fn metrics_require_progress_over_time() {
        let mut estimator = TransferMetricEstimator::default();
        assert_eq!(
            estimator.observe(1_000, 0, Some(1_000)),
            EphemeralTransferMetrics::unknown()
        );
        assert_eq!(
            estimator.observe(1_000, 100, Some(1_000)),
            EphemeralTransferMetrics::unknown()
        );
    }

    #[test]
    fn metrics_calculate_speed_and_eta_from_recent_samples() {
        let mut estimator = TransferMetricEstimator::new(5_000);
        estimator.observe(1_000, 0, Some(1_000));

        let metrics = estimator.observe(2_000, 250, Some(1_000));

        assert_eq!(metrics.bytes_per_second, Some(250));
        assert_eq!(metrics.eta_seconds, Some(3));
    }

    #[test]
    fn unknown_total_never_fabricates_eta() {
        let mut estimator = TransferMetricEstimator::default();
        estimator.observe(1_000, 1_000, None);

        let metrics = estimator.observe(2_000, 2_000, None);

        assert_eq!(metrics.bytes_per_second, Some(1_000));
        assert_eq!(metrics.eta_seconds, None);
    }

    #[test]
    fn completed_known_total_has_zero_eta() {
        let mut estimator = TransferMetricEstimator::default();
        estimator.observe(1_000, 0, Some(10));

        let metrics = estimator.observe(2_000, 10, Some(10));

        assert_eq!(metrics.bytes_per_second, Some(10));
        assert_eq!(metrics.eta_seconds, Some(0));
    }
}
