//! Stable, coarse-grained UniFFI-facing data boundary.
//!
//! The Android layer calls these APIs from background dispatchers. Long-running work is represented
//! by durable job identifiers and cooperative cancellation tokens so cancellation remains an
//! explicit application-level channel rather than depending on foreign-future cancellation semantics.

use crate::{CoreError, ErrorKind, MediaInfo, QualityChoice};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiSourceIdentity {
    pub provider: String,
    pub media_id: String,
    pub canonical_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiMediaSummary {
    pub source: FfiSourceIdentity,
    pub title: String,
    pub duration_ms: Option<u64>,
    pub thumbnail_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiQualityChoice {
    pub choice_id: String,
    pub label: String,
    pub estimated_bytes: Option<u64>,
    pub video_height: Option<u32>,
    pub audio_only: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum FfiErrorKind {
    InvalidInput,
    UnsupportedSource,
    NetworkUnavailable,
    NetworkTimeout,
    HttpStatus,
    SourceChanged,
    NoCompatibleFormat,
    InsufficientStorage,
    IntegrityFailure,
    MissingAsset,
    CorruptAsset,
    Persistence,
    Canceled,
    Internal,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiError {
    pub kind: FfiErrorKind,
    pub message: String,
    pub retryable: bool,
}

/// Cooperative cancellation channel that can safely cross the UniFFI boundary.
///
/// Long-running Rust operations receive a clone of this object and check `is_canceled` at bounded
/// interruption points. Android may call `cancel` from another thread without blocking the main
/// thread or relying on foreign-future cancellation behavior.
#[derive(Debug, uniffi::Object)]
pub struct FfiCancellationToken {
    canceled: AtomicBool,
}

#[uniffi::export]
impl FfiCancellationToken {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            canceled: AtomicBool::new(false),
        })
    }

    pub fn cancel(&self) {
        self.canceled.store(true, Ordering::Release);
    }

    pub fn is_canceled(&self) -> bool {
        self.canceled.load(Ordering::Acquire)
    }
}

impl FfiCancellationToken {
    /// Convert a requested cancellation into the same typed error exposed to Kotlin.
    pub fn check(&self) -> Result<(), CoreError> {
        if self.is_canceled() {
            Err(CoreError::new(
                ErrorKind::Canceled,
                "Operation canceled",
                false,
            ))
        } else {
            Ok(())
        }
    }
}

impl From<&MediaInfo> for FfiMediaSummary {
    fn from(value: &MediaInfo) -> Self {
        Self {
            source: FfiSourceIdentity {
                provider: value.source.provider.clone(),
                media_id: value.source.media_id.clone(),
                canonical_url: value.source.canonical_url.clone(),
            },
            title: value.title.clone(),
            duration_ms: value.duration_ms,
            thumbnail_url: value.thumbnail_url.clone(),
        }
    }
}

impl From<&QualityChoice> for FfiQualityChoice {
    fn from(value: &QualityChoice) -> Self {
        Self {
            choice_id: value.choice_id.clone(),
            label: value.label.clone(),
            estimated_bytes: value.estimated_bytes,
            video_height: value.video_height,
            audio_only: value.audio_only,
        }
    }
}

impl From<&CoreError> for FfiError {
    fn from(value: &CoreError) -> Self {
        Self {
            kind: match value.kind {
                ErrorKind::InvalidInput => FfiErrorKind::InvalidInput,
                ErrorKind::UnsupportedSource => FfiErrorKind::UnsupportedSource,
                ErrorKind::NetworkUnavailable => FfiErrorKind::NetworkUnavailable,
                ErrorKind::NetworkTimeout => FfiErrorKind::NetworkTimeout,
                ErrorKind::HttpStatus => FfiErrorKind::HttpStatus,
                ErrorKind::SourceChanged => FfiErrorKind::SourceChanged,
                ErrorKind::NoCompatibleFormat => FfiErrorKind::NoCompatibleFormat,
                ErrorKind::InsufficientStorage => FfiErrorKind::InsufficientStorage,
                ErrorKind::IntegrityFailure => FfiErrorKind::IntegrityFailure,
                ErrorKind::MissingAsset => FfiErrorKind::MissingAsset,
                ErrorKind::CorruptAsset => FfiErrorKind::CorruptAsset,
                ErrorKind::Persistence => FfiErrorKind::Persistence,
                ErrorKind::Canceled => FfiErrorKind::Canceled,
                ErrorKind::Internal => FfiErrorKind::Internal,
            },
            message: value.message.clone(),
            retryable: value.retryable,
        }
    }
}

#[uniffi::export]
pub fn ffi_core_identity() -> String {
    crate::core_identity().to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Compatibility, SourceIdentity};

    #[test]
    fn representative_types_round_trip_through_ffi_records() {
        let media = MediaInfo {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "one".into(),
                canonical_url: Some("https://fixture.invalid/one".into()),
            },
            title: "One".into(),
            duration_ms: Some(42),
            thumbnail_url: None,
            formats: Vec::new(),
            subtitles: Vec::new(),
        };
        let summary = FfiMediaSummary::from(&media);
        assert_eq!(summary.source.media_id, "one");
        assert_eq!(summary.duration_ms, Some(42));

        let choice = QualityChoice {
            choice_id: "720".into(),
            label: "720p".into(),
            estimated_bytes: Some(1024),
            video_height: Some(720),
            audio_only: false,
            compatibility: Compatibility::Preferred,
        };
        assert_eq!(FfiQualityChoice::from(&choice).label, "720p");
    }

    #[test]
    fn errors_map_to_kotlin_safe_categories() {
        let core = CoreError::new(ErrorKind::InsufficientStorage, "not enough space", false);
        let ffi = FfiError::from(&core);
        assert_eq!(ffi.kind, FfiErrorKind::InsufficientStorage);
        assert!(!ffi.retryable);
    }

    #[test]
    fn cancellation_is_cooperative_sticky_and_typed() {
        let token = FfiCancellationToken::new();
        assert!(!token.is_canceled());
        token.check().unwrap();

        token.cancel();
        assert!(token.is_canceled());
        token.cancel();
        assert!(token.is_canceled());

        let error = token.check().unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
        let ffi = FfiError::from(&error);
        assert_eq!(ffi.kind, FfiErrorKind::Canceled);
        assert!(!ffi.retryable);
    }
}
