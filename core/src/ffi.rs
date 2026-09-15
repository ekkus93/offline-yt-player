//! Stable, coarse-grained UniFFI-facing data boundary.
//!
//! The Android layer calls these APIs from background dispatchers. Long-running work is represented
//! by durable job identifiers so cancellation remains an explicit application-level channel rather
//! than depending on foreign-future cancellation semantics.

use crate::{CoreError, ErrorKind, MediaInfo, QualityChoice};

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
    SourceChanged,
    NoCompatibleFormat,
    StorageFull,
    IntegrityFailure,
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
                ErrorKind::SourceChanged => FfiErrorKind::SourceChanged,
                ErrorKind::NoCompatibleFormat => FfiErrorKind::NoCompatibleFormat,
                ErrorKind::StorageFull => FfiErrorKind::StorageFull,
                ErrorKind::IntegrityFailure => FfiErrorKind::IntegrityFailure,
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
        let core = CoreError::new(ErrorKind::StorageFull, "not enough space", false);
        let ffi = FfiError::from(&core);
        assert_eq!(ffi.kind, FfiErrorKind::StorageFull);
        assert!(!ffi.retryable);
    }
}
