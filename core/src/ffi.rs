//! Stable, coarse-grained UniFFI-facing data boundary.
//!
//! The Android layer calls these APIs from background dispatchers. Long-running work is represented
//! by durable job identifiers and cooperative cancellation tokens so cancellation remains an
//! explicit application-level channel rather than depending on foreign-future cancellation semantics.

use crate::{
    CoreError, DownloadState, DurableDownloadSnapshot, ErrorKind, LibraryItem, LibraryStore,
    MediaInfo, PlaybackPositionPolicy, QualityChoice, persist_playback_position,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiSourceIdentity { pub provider: String, pub media_id: String, pub canonical_url: Option<String> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiMediaSummary { pub source: FfiSourceIdentity, pub title: String, pub duration_ms: Option<u64>, pub thumbnail_url: Option<String> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiQualityChoice { pub choice_id: String, pub label: String, pub estimated_bytes: Option<u64>, pub video_height: Option<u32>, pub audio_only: bool }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryItem { pub item_id: String, pub source: FfiSourceIdentity, pub display_title: String, pub duration_ms: Option<u64>, pub quality_label: String, pub created_at_epoch_ms: u64, pub playback_position_ms: u64, pub completed: bool }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum FfiDownloadState { Queued, Resolving, Downloading, Paused, RetryWait, Failed, Verifying, Completed, Canceled }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDurableDownloadSnapshot { pub job_id: String, pub state: FfiDownloadState, pub bytes_downloaded: u64, pub total_bytes: Option<u64>, pub attempt: u32, pub retry_at_epoch_ms: Option<u64>, pub last_error: Option<FfiError> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum FfiErrorKind { InvalidInput, UnsupportedSource, NetworkUnavailable, NetworkTimeout, HttpStatus, SourceChanged, NoCompatibleFormat, InsufficientStorage, IntegrityFailure, MissingAsset, CorruptAsset, Persistence, Canceled, Internal }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiError { pub kind: FfiErrorKind, pub message: String, pub retryable: bool }
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiCoreServiceOpenError { #[error("persistence error: {message}")] Persistence { message: String } }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryListResult { pub items: Vec<FfiLibraryItem>, pub error: Option<FfiError> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryGetResult { pub item: Option<FfiLibraryItem>, pub error: Option<FfiError> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryDeleteResult { pub deleted: bool, pub error: Option<FfiError> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiPlaybackPositionResult { pub persisted: bool, pub error: Option<FfiError> }
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadQueueResult { pub jobs: Vec<FfiDurableDownloadSnapshot>, pub error: Option<FfiError> }

#[derive(Debug, uniffi::Object)]
pub struct FfiCoreService { library: LibraryStore }

#[uniffi::export]
impl FfiCoreService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiCoreServiceOpenError> {
        LibraryStore::open(&database_path).map(|library| Arc::new(Self { library })).map_err(|error| FfiCoreServiceOpenError::Persistence { message: error.message })
    }
    pub fn library_list(&self, query: Option<String>) -> FfiLibraryListResult { match self.library.list(query.as_deref()) { Ok(items) => FfiLibraryListResult { items: items.iter().map(FfiLibraryItem::from).collect(), error: None }, Err(error) => FfiLibraryListResult { items: Vec::new(), error: Some(FfiError::from(&error)) } } }
    pub fn library_get(&self, item_id: String) -> FfiLibraryGetResult { match self.library.get(&item_id) { Ok(item) => FfiLibraryGetResult { item: item.as_ref().map(FfiLibraryItem::from), error: None }, Err(error) => FfiLibraryGetResult { item: None, error: Some(FfiError::from(&error)) } } }
    pub fn library_delete(&self, item_id: String) -> FfiLibraryDeleteResult { match self.library.delete(&item_id) { Ok(item) => FfiLibraryDeleteResult { deleted: item.is_some(), error: None }, Err(error) => FfiLibraryDeleteResult { deleted: false, error: Some(FfiError::from(&error)) } } }
    pub fn playback_position_save(&self, item_id: String, position_ms: u64, duration_ms: Option<u64>) -> FfiPlaybackPositionResult {
        match persist_playback_position(&self.library, &item_id, position_ms, duration_ms, PlaybackPositionPolicy::default()) {
            Ok(persisted) => FfiPlaybackPositionResult { persisted, error: None },
            Err(error) => FfiPlaybackPositionResult { persisted: false, error: Some(FfiError::from(&error)) },
        }
    }
    pub fn download_queue(&self) -> FfiDownloadQueueResult { match self.library.load_download_snapshots() { Ok(jobs) => FfiDownloadQueueResult { jobs: jobs.iter().map(FfiDurableDownloadSnapshot::from).collect(), error: None }, Err(error) => FfiDownloadQueueResult { jobs: Vec::new(), error: Some(FfiError::from(&error)) } } }
}

#[derive(Debug, uniffi::Object)]
pub struct FfiCancellationToken { canceled: AtomicBool }
#[uniffi::export]
impl FfiCancellationToken {
    #[uniffi::constructor] pub fn new() -> Arc<Self> { Arc::new(Self { canceled: AtomicBool::new(false) }) }
    pub fn cancel(&self) { self.canceled.store(true, Ordering::Release); }
    pub fn is_canceled(&self) -> bool { self.canceled.load(Ordering::Acquire) }
    pub fn check(&self) -> Result<(), CoreError> { if self.is_canceled() { Err(CoreError::new(ErrorKind::Canceled, "Operation canceled", false)) } else { Ok(()) } }
}

impl From<&MediaInfo> for FfiMediaSummary { fn from(value: &MediaInfo) -> Self { Self { source: FfiSourceIdentity { provider: value.source.provider.clone(), media_id: value.source.media_id.clone(), canonical_url: value.source.canonical_url.clone() }, title: value.title.clone(), duration_ms: value.duration_ms, thumbnail_url: value.thumbnail_url.clone() } } }
impl From<&QualityChoice> for FfiQualityChoice { fn from(value: &QualityChoice) -> Self { Self { choice_id: value.choice_id.clone(), label: value.label.clone(), estimated_bytes: value.estimated_bytes, video_height: value.video_height, audio_only: value.audio_only } } }
impl From<&LibraryItem> for FfiLibraryItem { fn from(value: &LibraryItem) -> Self { Self { item_id: value.item_id.clone(), source: FfiSourceIdentity { provider: value.source.provider.clone(), media_id: value.source.media_id.clone(), canonical_url: value.source.canonical_url.clone() }, display_title: value.display_title.clone(), duration_ms: value.duration_ms, quality_label: value.quality_label.clone(), created_at_epoch_ms: value.created_at_epoch_ms, playback_position_ms: value.playback_position_ms, completed: value.completed } } }
impl From<DownloadState> for FfiDownloadState { fn from(value: DownloadState) -> Self { match value { DownloadState::Queued => Self::Queued, DownloadState::Resolving => Self::Resolving, DownloadState::Downloading => Self::Downloading, DownloadState::Paused => Self::Paused, DownloadState::RetryWait => Self::RetryWait, DownloadState::Failed => Self::Failed, DownloadState::Verifying => Self::Verifying, DownloadState::Completed => Self::Completed, DownloadState::Canceled => Self::Canceled } } }
impl From<&DurableDownloadSnapshot> for FfiDurableDownloadSnapshot { fn from(value: &DurableDownloadSnapshot) -> Self { Self { job_id: value.job_id.clone(), state: value.state.into(), bytes_downloaded: value.bytes_downloaded, total_bytes: value.total_bytes, attempt: value.attempt, retry_at_epoch_ms: value.retry_at_epoch_ms, last_error: value.last_error.as_ref().map(FfiError::from) } } }
impl From<&CoreError> for FfiError { fn from(value: &CoreError) -> Self { Self { kind: match value.kind { ErrorKind::InvalidInput => FfiErrorKind::InvalidInput, ErrorKind::UnsupportedSource => FfiErrorKind::UnsupportedSource, ErrorKind::NetworkUnavailable => FfiErrorKind::NetworkUnavailable, ErrorKind::NetworkTimeout => FfiErrorKind::NetworkTimeout, ErrorKind::HttpStatus => FfiErrorKind::HttpStatus, ErrorKind::SourceChanged => FfiErrorKind::SourceChanged, ErrorKind::NoCompatibleFormat => FfiErrorKind::NoCompatibleFormat, ErrorKind::InsufficientStorage => FfiErrorKind::InsufficientStorage, ErrorKind::IntegrityFailure => FfiErrorKind::IntegrityFailure, ErrorKind::MissingAsset => FfiErrorKind::MissingAsset, ErrorKind::CorruptAsset => FfiErrorKind::CorruptAsset, ErrorKind::Persistence => FfiErrorKind::Persistence, ErrorKind::Canceled => FfiErrorKind::Canceled, ErrorKind::Internal => FfiErrorKind::Internal }, message: value.message.clone(), retryable: value.retryable } } }

#[uniffi::export] pub fn ffi_core_identity() -> String { crate::core_identity().to_owned() }
#[uniffi::export] pub fn ffi_max_concurrent_downloads() -> u32 { u32::try_from(crate::MAX_CONCURRENT_DOWNLOADS).expect("core concurrency ceiling fits u32") }
#[uniffi::export] pub fn ffi_bounded_download_concurrency(requested: u32) -> u32 { u32::try_from(crate::bounded_download_concurrency(requested as usize)).expect("bounded concurrency fits u32") }

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Compatibility, LibraryItem, LocalAsset, MediaKind, SourceIdentity};
    #[test] fn representative_types_round_trip_through_ffi_records() { let media = MediaInfo { source: SourceIdentity { provider: "fixture".into(), media_id: "one".into(), canonical_url: Some("https://fixture.invalid/one".into()) }, title: "One".into(), duration_ms: Some(42), thumbnail_url: None, formats: Vec::new(), subtitles: Vec::new() }; let summary = FfiMediaSummary::from(&media); assert_eq!(summary.source.media_id, "one"); let choice = QualityChoice { choice_id: "720".into(), label: "720p".into(), estimated_bytes: Some(1024), video_height: Some(720), audio_only: false, compatibility: Compatibility::Preferred }; assert_eq!(FfiQualityChoice::from(&choice).label, "720p"); }
    #[test] fn errors_map_to_kotlin_safe_categories() { let core = CoreError::new(ErrorKind::InsufficientStorage, "not enough space", false); let ffi = FfiError::from(&core); assert_eq!(ffi.kind, FfiErrorKind::InsufficientStorage); assert_eq!(ffi.message, "not enough space"); assert!(!ffi.retryable); }
    #[test] fn cancellation_is_sticky_and_idempotent() { let token = FfiCancellationToken::new(); assert!(!token.is_canceled()); assert!(token.check().is_ok()); token.cancel(); token.cancel(); assert!(token.is_canceled()); assert_eq!(token.check().unwrap_err().kind, ErrorKind::Canceled); }
    #[test] fn playback_position_save_round_trips_through_service() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = LibraryItem { item_id: "item-1".into(), source: SourceIdentity { provider: "fixture".into(), media_id: "one".into(), canonical_url: None }, display_title: "One".into(), duration_ms: Some(60_000), quality_label: "720p".into(), assets: vec![LocalAsset { asset_id: "video".into(), kind: MediaKind::Video, relative_path: "items/item-1/video.mp4".into(), bytes: 1, sha256: None, mime_type: Some("video/mp4".into()) }], created_at_epoch_ms: 1, playback_position_ms: 0, completed: true };
        store.promote_completed("job-1", &item).unwrap();
        let service = FfiCoreService { library: store };
        let result = service.playback_position_save("item-1".into(), 55_000, Some(60_000));
        assert!(result.persisted); assert!(result.error.is_none());
        assert_eq!(service.library.get("item-1").unwrap().unwrap().playback_position_ms, 60_000);
    }
}
