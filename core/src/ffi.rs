//! Stable, coarse-grained UniFFI-facing data boundary.
//!
//! The Android layer calls these APIs from background dispatchers. Long-running work is represented
//! by durable job identifiers and cooperative cancellation tokens so cancellation remains an
//! explicit application-level channel rather than depending on foreign-future cancellation semantics.

use crate::{
    CoreError, DownloadState, DurableDownloadSnapshot, ErrorKind, LibraryItem, LibraryStore,
    MediaInfo, QualityChoice,
};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiSourceIdentity {
    pub provider: String,
    pub media_id: String,
    pub canonical_url: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiSubtitleChoice {
    pub track_id: String,
    pub language: String,
    pub label: Option<String>,
    pub format: String,
    pub auto_generated: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiAudioChoice {
    pub format_id: String,
    pub label: String,
    pub container: String,
    pub bitrate_bps: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiMediaSummary {
    pub source: FfiSourceIdentity,
    pub title: String,
    pub duration_ms: Option<u64>,
    pub thumbnail_url: Option<String>,
    pub subtitle_options: Vec<FfiSubtitleChoice>,
    pub audio_options: Vec<FfiAudioChoice>,
    pub container_options: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiQualityChoice {
    pub choice_id: String,
    pub label: String,
    pub estimated_bytes: Option<u64>,
    pub video_height: Option<u32>,
    pub audio_only: bool,
    pub separate_audio: bool,
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
pub enum FfiDownloadState {
    Queued,
    Resolving,
    Downloading,
    Paused,
    RetryWait,
    Failed,
    Verifying,
    Completed,
    Canceled,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDurableDownloadSnapshot {
    pub job_id: String,
    pub state: FfiDownloadState,
    pub bytes_downloaded: u64,
    pub total_bytes: Option<u64>,
    pub attempt: u32,
    pub retry_at_epoch_ms: Option<u64>,
    pub last_error: Option<FfiError>,
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
    #[error("persistence error: {details}")]
    Persistence { details: String },
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

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiDownloadQueueResult {
    pub jobs: Vec<FfiDurableDownloadSnapshot>,
    pub error: Option<FfiError>,
}

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
                details: crate::security::redact_sensitive(&error.message),
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

    /// Return durable queue state as the authoritative platform-visible download state.
    pub fn download_queue(&self) -> FfiDownloadQueueResult {
        match self.library.load_download_snapshots() {
            Ok(jobs) => FfiDownloadQueueResult {
                jobs: jobs.iter().map(FfiDurableDownloadSnapshot::from).collect(),
                error: None,
            },
            Err(error) => FfiDownloadQueueResult {
                jobs: Vec::new(),
                error: Some(FfiError::from(&error)),
            },
        }
    }
}

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
            subtitle_options: value
                .subtitles
                .iter()
                .map(|track| FfiSubtitleChoice {
                    track_id: track.track_id.clone(),
                    language: track.format.language.clone(),
                    label: track.format.label.clone(),
                    format: track.format.format.clone(),
                    auto_generated: track.format.auto_generated,
                })
                .collect(),
            audio_options: value
                .formats
                .iter()
                .filter(|format| {
                    format.role == crate::StreamRole::AudioOnly && format.compatible_direct_play
                })
                .map(|format| FfiAudioChoice {
                    format_id: format.format_id.clone(),
                    label: format
                        .audio
                        .as_ref()
                        .and_then(|audio| audio.language.as_deref())
                        .map_or_else(
                            || match format.bitrate_bps {
                                Some(bits) => format!("{} kbps {}", bits / 1000, format.container),
                                None => format!("{} audio", format.container),
                            },
                            |language| format!("{language} · {}", format.container),
                        ),
                    container: format.container.clone(),
                    bitrate_bps: format.bitrate_bps,
                })
                .collect(),
            container_options: {
                let mut containers = value
                    .formats
                    .iter()
                    .filter(|format| format.video.is_some() && format.compatible_direct_play)
                    .map(|format| format.container.clone())
                    .collect::<Vec<_>>();
                containers.sort();
                containers.dedup();
                containers
            },
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
            separate_audio: value.compatibility == crate::Compatibility::RequiresSeparateAssets,
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

impl From<DownloadState> for FfiDownloadState {
    fn from(value: DownloadState) -> Self {
        match value {
            DownloadState::Queued => Self::Queued,
            DownloadState::Resolving => Self::Resolving,
            DownloadState::Downloading => Self::Downloading,
            DownloadState::Paused => Self::Paused,
            DownloadState::RetryWait => Self::RetryWait,
            DownloadState::Failed => Self::Failed,
            DownloadState::Verifying => Self::Verifying,
            DownloadState::Completed => Self::Completed,
            DownloadState::Canceled => Self::Canceled,
        }
    }
}

impl From<&DurableDownloadSnapshot> for FfiDurableDownloadSnapshot {
    fn from(value: &DurableDownloadSnapshot) -> Self {
        Self {
            job_id: value.job_id.clone(),
            state: value.state.into(),
            bytes_downloaded: value.bytes_downloaded,
            total_bytes: value.total_bytes,
            attempt: value.attempt,
            retry_at_epoch_ms: value.retry_at_epoch_ms,
            last_error: value.last_error.as_ref().map(FfiError::from),
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
            // No CoreError diagnostic may leave UniFFI with credential-bearing
            // headers, signed query strings, or oversized untrusted text.
            message: crate::security::redact_sensitive(&value.message),
            retryable: value.retryable,
        }
    }
}

#[uniffi::export]
pub fn ffi_core_identity() -> String {
    crate::core_identity().to_owned()
}

#[uniffi::export]
pub fn ffi_max_concurrent_downloads() -> u32 {
    u32::try_from(crate::MAX_CONCURRENT_DOWNLOADS).expect("core concurrency ceiling fits u32")
}

#[uniffi::export]
pub fn ffi_bounded_download_concurrency(requested: u32) -> u32 {
    u32::try_from(crate::bounded_download_concurrency(requested as usize))
        .expect("bounded concurrency fits u32")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Compatibility, MediaFormat, SourceIdentity, StreamRole, VideoFormat};

    #[test]
    fn all_core_error_categories_preserve_kind_and_retryability_across_ffi() {
        let cases = [
            (ErrorKind::InvalidInput, FfiErrorKind::InvalidInput),
            (
                ErrorKind::UnsupportedSource,
                FfiErrorKind::UnsupportedSource,
            ),
            (
                ErrorKind::NetworkUnavailable,
                FfiErrorKind::NetworkUnavailable,
            ),
            (ErrorKind::NetworkTimeout, FfiErrorKind::NetworkTimeout),
            (ErrorKind::HttpStatus, FfiErrorKind::HttpStatus),
            (ErrorKind::SourceChanged, FfiErrorKind::SourceChanged),
            (
                ErrorKind::NoCompatibleFormat,
                FfiErrorKind::NoCompatibleFormat,
            ),
            (
                ErrorKind::InsufficientStorage,
                FfiErrorKind::InsufficientStorage,
            ),
            (ErrorKind::IntegrityFailure, FfiErrorKind::IntegrityFailure),
            (ErrorKind::MissingAsset, FfiErrorKind::MissingAsset),
            (ErrorKind::CorruptAsset, FfiErrorKind::CorruptAsset),
            (ErrorKind::Persistence, FfiErrorKind::Persistence),
            (ErrorKind::Canceled, FfiErrorKind::Canceled),
            (ErrorKind::Internal, FfiErrorKind::Internal),
        ];
        for (core_kind, expected) in cases {
            for retryable in [false, true] {
                let core = CoreError::new(core_kind.clone(), "sanitized fixture", retryable);
                let mapped = FfiError::from(&core);
                assert_eq!(mapped.kind, expected, "core kind {core_kind:?}");
                assert_eq!(mapped.retryable, retryable);
                assert_eq!(mapped.message, "sanitized fixture");
            }
        }
    }

    #[test]
    fn ffi_records_preserve_optional_and_maximum_unsigned_values() {
        let snapshot = DurableDownloadSnapshot {
            job_id: "max-uint".into(),
            state: DownloadState::RetryWait,
            bytes_downloaded: u64::MAX,
            total_bytes: Some(u64::MAX),
            attempt: u32::MAX,
            retry_at_epoch_ms: Some(u64::MAX),
            last_error: None,
        };
        let mapped = FfiDurableDownloadSnapshot::from(&snapshot);
        assert_eq!(mapped.job_id, "max-uint");
        assert_eq!(mapped.state, FfiDownloadState::RetryWait);
        assert_eq!(mapped.bytes_downloaded, u64::MAX);
        assert_eq!(mapped.total_bytes, Some(u64::MAX));
        assert_eq!(mapped.attempt, u32::MAX);
        assert_eq!(mapped.retry_at_epoch_ms, Some(u64::MAX));
        assert_eq!(mapped.last_error, None);

        let choice = QualityChoice {
            choice_id: "format:audio-1".into(),
            label: "Audio".into(),
            estimated_bytes: Some(u64::MAX),
            video_height: None,
            audio_only: true,
            compatibility: Compatibility::RequiresSeparateAssets,
        };
        let mapped = FfiQualityChoice::from(&choice);
        assert_eq!(mapped.choice_id, choice.choice_id);
        assert_eq!(mapped.estimated_bytes, Some(u64::MAX));
        assert_eq!(mapped.video_height, None);
        assert!(mapped.audio_only);
        assert!(mapped.separate_audio);
    }

    #[test]
    fn zero_unsigned_fields_preserve_presence_and_bounded_text_across_ffi_records() {
        let zero = DurableDownloadSnapshot {
            job_id: "zero-values".into(),
            state: DownloadState::Queued,
            bytes_downloaded: 0,
            total_bytes: Some(0),
            attempt: 0,
            retry_at_epoch_ms: Some(0),
            last_error: Some(CoreError::new(
                ErrorKind::InvalidInput,
                "sanitized error",
                false,
            )),
        };
        let mapped = FfiDurableDownloadSnapshot::from(&zero);
        assert_eq!(mapped.bytes_downloaded, 0);
        assert_eq!(mapped.total_bytes, Some(0));
        assert_eq!(mapped.attempt, 0);
        assert_eq!(mapped.retry_at_epoch_ms, Some(0));
        assert_eq!(mapped.last_error.unwrap().kind, FfiErrorKind::InvalidInput);

        let quality = QualityChoice {
            choice_id: "format:zero".into(),
            label: "Unknown size".into(),
            estimated_bytes: Some(0),
            video_height: None,
            audio_only: false,
            compatibility: Compatibility::Preferred,
        };
        let mapped = FfiQualityChoice::from(&quality);
        assert_eq!(mapped.estimated_bytes, Some(0));
        assert_eq!(mapped.video_height, None);
        assert!(!mapped.separate_audio);

        let title = "T".repeat(crate::MAX_PROVIDER_TITLE_CHARS);
        let media = MediaInfo {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "zero".into(),
                canonical_url: None,
            },
            title: title.clone(),
            duration_ms: Some(0),
            thumbnail_url: None,
            formats: Vec::new(),
            subtitles: Vec::new(),
        };
        let summary = FfiMediaSummary::from(&media);
        assert_eq!(summary.title, title);
        assert_eq!(
            summary.title.chars().count(),
            crate::MAX_PROVIDER_TITLE_CHARS
        );
        assert_eq!(summary.duration_ms, Some(0));
        assert_eq!(summary.source.canonical_url, None);
        assert_eq!(summary.thumbnail_url, None);
        assert!(summary.audio_options.is_empty());
        assert!(summary.subtitle_options.is_empty());
        assert!(summary.container_options.is_empty());

        let item = LibraryItem {
            item_id: "zero".into(),
            source: media.source,
            display_title: summary.title,
            duration_ms: None,
            quality_label: "Fixture".into(),
            assets: Vec::new(),
            created_at_epoch_ms: 0,
            playback_position_ms: 0,
            completed: false,
        };
        let result = FfiLibraryItem::from(&item);
        assert_eq!(result.created_at_epoch_ms, 0);
        assert_eq!(result.playback_position_ms, 0);
        assert_eq!(result.duration_ms, None);
        assert!(!result.completed);
    }

    #[test]
    fn same_label_audio_choices_keep_distinct_format_ids_through_ffi() {
        let audio_format = |id: &str| MediaFormat {
            format_id: id.into(),
            container: "m4a".into(),
            mime_type: Some("audio/mp4".into()),
            role: StreamRole::AudioOnly,
            bitrate_bps: Some(128_000),
            content_length: None,
            video: None,
            audio: Some(crate::AudioFormat {
                codec: "aac".into(),
                bitrate_bps: Some(128_000),
                channels: Some(2),
                language: None,
            }),
            compatible_direct_play: true,
        };
        let media = MediaInfo {
            source: SourceIdentity {
                provider: "youtube".into(),
                media_id: "fixture-media".into(),
                canonical_url: None,
            },
            title: "Offline fixture".into(),
            duration_ms: None,
            thumbnail_url: None,
            formats: vec![audio_format("format-a"), audio_format("format-b")],
            subtitles: Vec::new(),
        };
        let converted = FfiMediaSummary::from(&media);
        assert_eq!(converted.duration_ms, None);
        assert_eq!(converted.source.canonical_url, None);
        assert_eq!(converted.audio_options.len(), 2);
        assert_eq!(converted.audio_options[0].format_id, "format-a");
        assert_eq!(converted.audio_options[1].format_id, "format-b");
        assert_eq!(
            converted.audio_options[0].label,
            converted.audio_options[1].label
        );
        assert!(converted.container_options.is_empty());
    }

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
            formats: vec![MediaFormat {
                format_id: "video-720p".into(),
                container: "mp4".into(),
                mime_type: Some("video/mp4".into()),
                role: StreamRole::Combined,
                bitrate_bps: Some(1_000_000),
                content_length: Some(1024),
                video: Some(VideoFormat {
                    width: 1280,
                    height: 720,
                    fps: Some(30),
                    codec: "h264".into(),
                }),
                audio: None,
                compatible_direct_play: true,
            }],
            subtitles: Vec::new(),
        };
        let summary = FfiMediaSummary::from(&media);
        assert_eq!(summary.source.media_id, "one");
        let choice = QualityChoice {
            choice_id: "720".into(),
            label: "720p".into(),
            estimated_bytes: Some(1024),
            video_height: Some(720),
            audio_only: false,
            compatibility: Compatibility::Preferred,
        };
        assert_eq!(FfiQualityChoice::from(&choice).label, "720p");
        assert!(summary.subtitle_options.is_empty());
        assert!(summary.audio_options.is_empty());
        assert_eq!(summary.container_options, vec!["mp4"]);
    }

    #[test]
    fn errors_map_to_kotlin_safe_categories() {
        let core = CoreError::new(ErrorKind::InsufficientStorage, "not enough space", false);
        let ffi = FfiError::from(&core);
        assert_eq!(ffi.kind, FfiErrorKind::InsufficientStorage);
        assert!(!ffi.retryable);
    }

    #[test]
    fn core_service_exposes_durable_queue_and_survives_reopen() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        let snapshots = [
            DurableDownloadSnapshot {
                job_id: "queued".into(),
                state: DownloadState::Queued,
                bytes_downloaded: 0,
                total_bytes: Some(100),
                attempt: 0,
                retry_at_epoch_ms: None,
                last_error: None,
            },
            DurableDownloadSnapshot {
                job_id: "retrying".into(),
                state: DownloadState::RetryWait,
                bytes_downloaded: 40,
                total_bytes: Some(100),
                attempt: 2,
                retry_at_epoch_ms: Some(123_456),
                last_error: Some(CoreError::new(ErrorKind::NetworkTimeout, "timeout", true)),
            },
            DurableDownloadSnapshot {
                job_id: "paused".into(),
                state: DownloadState::Paused,
                bytes_downloaded: 60,
                total_bytes: Some(100),
                attempt: 1,
                retry_at_epoch_ms: None,
                last_error: None,
            },
            DurableDownloadSnapshot {
                job_id: "completed".into(),
                state: DownloadState::Completed,
                bytes_downloaded: 100,
                total_bytes: Some(100),
                attempt: 1,
                retry_at_epoch_ms: None,
                last_error: None,
            },
        ];
        for snapshot in &snapshots {
            store.save_download_snapshot(snapshot).unwrap();
        }
        drop(store);

        let service = FfiCoreService::open(database.to_string_lossy().into_owned()).unwrap();
        let result = service.download_queue();
        assert!(result.error.is_none());
        assert_eq!(result.jobs.len(), snapshots.len());

        let retrying = result
            .jobs
            .iter()
            .find(|job| job.job_id == "retrying")
            .unwrap();
        assert_eq!(retrying.state, FfiDownloadState::RetryWait);
        assert_eq!(retrying.bytes_downloaded, 40);
        assert_eq!(retrying.attempt, 2);
        assert_eq!(retrying.retry_at_epoch_ms, Some(123_456));
        assert_eq!(
            retrying.last_error.as_ref().map(|error| &error.kind),
            Some(&FfiErrorKind::NetworkTimeout)
        );
    }

    #[test]
    fn concurrency_policy_maps_through_ffi_without_exceeding_core_ceiling() {
        assert_eq!(
            ffi_max_concurrent_downloads(),
            crate::MAX_CONCURRENT_DOWNLOADS as u32
        );
        assert_eq!(ffi_bounded_download_concurrency(0), 1);
        assert_eq!(ffi_bounded_download_concurrency(2), 2);
        assert_eq!(
            ffi_bounded_download_concurrency(u32::MAX),
            crate::MAX_CONCURRENT_DOWNLOADS as u32
        );
    }

    #[test]
    fn cancellation_is_cooperative_sticky_and_typed() {
        let token = FfiCancellationToken::new();
        assert!(!token.is_canceled());
        assert!(token.check().is_ok());
        token.cancel();
        let error = token.check().unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
        assert!(!error.retryable);
    }


    #[test]
    fn all_ffi_error_variants_redact_untrusted_private_diagnostics() {
        let marker = "PRIVATE_FFI_DIAGNOSTIC_TOKEN_706";
        let diagnostics = [
            format!("Authorization: Bearer {marker}"),
            format!("Cookie: session={marker}"),
            format!("GET https://cdn.fixture.invalid/video?sig={marker}#fragment"),
            format!("https://user:{marker}@cdn.fixture.invalid/asset"),
            format!("Proxy-Authorization: Basic {marker}"),
            format!("Authorization: Bearer {marker}\\n  {marker}"),
            format!("GET https%3A%2F%2Fcdn.fixture.invalid%2Ffile%3Fsig%3D{marker}"),
            format!("https://cdn.fixture.invalid/video?sig={marker}{}", "x".repeat(100_000)),
        ];
        for kind in [
            ErrorKind::NetworkUnavailable,
            ErrorKind::NetworkTimeout,
            ErrorKind::HttpStatus,
            ErrorKind::SourceChanged,
            ErrorKind::Persistence,
            ErrorKind::Internal,
        ] {
            for diagnostic in &diagnostics {
                let raw = CoreError::new(kind.clone(), diagnostic.clone(), true);
                let mapped = FfiError::from(&raw);
                assert_eq!(mapped.retryable, raw.retryable);
                assert!(!mapped.message.contains(marker));
                assert!(
                    mapped.message.contains("REDACTED"),
                    "sensitive diagnostic was not redacted: {:?}",
                    mapped.message
                );
                assert!(mapped.message.len() < 1024, "test diagnostic must stay bounded");
                assert_eq!(mapped.kind, FfiError::from(&CoreError::new(kind.clone(), "ok", true)).kind);
            }
        }
        let safe = CoreError::new(ErrorKind::NetworkUnavailable, "network unavailable", true);
        assert_eq!(FfiError::from(&safe).message, "network unavailable");
    }

    #[test]
    fn network_diagnostic_secret_markers_do_not_cross_ffi_error_boundary() {
        let markers = [
            "SIGNED_QUERY_SECRET",
            "TOKEN_SECRET",
            "COOKIE_SECRET",
            "BEARER_SECRET",
        ];
        let core = CoreError::new(
            ErrorKind::NetworkUnavailable,
            "Network request failed before a response was received",
            true,
        );
        let ffi = FfiError::from(&core);
        let rendered = format!("{ffi:?}");
        for marker in markers {
            assert!(!rendered.contains(marker));
        }
    }
}
