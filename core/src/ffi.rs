//! Stable, coarse-grained UniFFI-facing data boundary.
//!
//! The Android layer calls these APIs from background dispatchers. Long-running work is represented
//! by durable job identifiers and cooperative cancellation tokens so cancellation remains an
//! explicit application-level channel rather than depending on foreign-future cancellation semantics.

use crate::{CoreError, ErrorKind, LibraryItem, LibraryStore, MediaInfo, QualityChoice};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

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

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryItem {
    pub item_id: String,
    pub source: FfiSourceIdentity,
    pub display_title: String,
    pub duration_ms: Option<u64>,
    pub quality_label: String,
    pub created_at_epoch_ms: u64,
    pub playback_position_ms: u64,
    pub completed: bool,
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

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiCoreServiceOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryListResult {
    pub items: Vec<FfiLibraryItem>,
    pub error: Option<FfiError>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryGetResult {
    pub item: Option<FfiLibraryItem>,
    pub error: Option<FfiError>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryDeleteResult {
    pub deleted: bool,
    pub error: Option<FfiError>,
}

/// Coarse application service exported through UniFFI.
///
/// The service owns portable persistence and exposes operation-sized calls rather than leaking
/// SQLite handles or repository internals across the language boundary. Android must invoke these
/// potentially blocking calls through `CoreCallDispatcher`.
#[derive(Debug, uniffi::Object)]
pub struct FfiCoreService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiCoreService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiCoreServiceOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiCoreServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn library_list(&self, query: Option<String>) -> FfiLibraryListResult {
        match self.library.list(query.as_deref()) {
            Ok(items) => FfiLibraryListResult {
                items: items.iter().map(FfiLibraryItem::from).collect(),
                error: None,
            },
            Err(error) => FfiLibraryListResult {
                items: Vec::new(),
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn library_get(&self, item_id: String) -> FfiLibraryGetResult {
        match self.library.get(&item_id) {
            Ok(item) => FfiLibraryGetResult {
                item: item.as_ref().map(FfiLibraryItem::from),
                error: None,
            },
            Err(error) => FfiLibraryGetResult {
                item: None,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn library_delete(&self, item_id: String) -> FfiLibraryDeleteResult {
        match self.library.delete(&item_id) {
            Ok(item) => FfiLibraryDeleteResult {
                deleted: item.is_some(),
                error: None,
            },
            Err(error) => FfiLibraryDeleteResult {
                deleted: false,
                error: Some(FfiError::from(&error)),
            },
        }
    }
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

impl From<&LibraryItem> for FfiLibraryItem {
    fn from(value: &LibraryItem) -> Self {
        Self {
            item_id: value.item_id.clone(),
            source: FfiSourceIdentity {
                provider: value.source.provider.clone(),
                media_id: value.source.media_id.clone(),
                canonical_url: value.source.canonical_url.clone(),
            },
            display_title: value.display_title.clone(),
            duration_ms: value.duration_ms,
            quality_label: value.quality_label.clone(),
            created_at_epoch_ms: value.created_at_epoch_ms,
            playback_position_ms: value.playback_position_ms,
            completed: value.completed,
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

    #[test]
    fn core_service_exposes_coarse_library_operations() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let service = FfiCoreService::open(database.to_string_lossy().into_owned()).unwrap();

        let listed = service.library_list(None);
        assert!(listed.error.is_none());
        assert!(listed.items.is_empty());

        let missing = service.library_get("missing".into());
        assert!(missing.error.is_none());
        assert!(missing.item.is_none());

        let deleted = service.library_delete("missing".into());
        assert!(deleted.error.is_none());
        assert!(!deleted.deleted);
    }

    #[test]
    fn core_service_open_failure_is_typed_not_panicking() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("missing-parent").join("library.sqlite3");
        let error = FfiCoreService::open(database.to_string_lossy().into_owned()).unwrap_err();

        assert!(matches!(
            error,
            FfiCoreServiceOpenError::Persistence { .. }
        ));
    }
}
