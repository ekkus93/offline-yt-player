use crate::domain::CoreError;
use crate::state::DownloadState;
use serde::{Deserialize, Serialize};

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
}
