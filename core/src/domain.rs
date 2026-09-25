use serde::{Deserialize, Serialize};
use std::fmt;

/// Provider-independent source identity stored with every library item.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct SourceIdentity {
    pub provider: String,
    pub media_id: String,
    pub canonical_url: Option<String>,
}

impl SourceIdentity {
    #[must_use]
    pub fn new(provider: impl Into<String>, media_id: impl Into<String>) -> Self {
        Self {
            provider: provider.into(),
            media_id: media_id.into(),
            canonical_url: None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MediaKind {
    Video,
    Audio,
    Subtitle,
    Thumbnail,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum StreamRole {
    Combined,
    VideoOnly,
    AudioOnly,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct VideoFormat {
    pub width: u32,
    pub height: u32,
    pub fps: Option<u32>,
    pub codec: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AudioFormat {
    pub codec: String,
    pub bitrate_bps: Option<u64>,
    pub channels: Option<u8>,
    pub language: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SubtitleFormat {
    pub language: String,
    pub label: Option<String>,
    pub format: String,
    pub auto_generated: bool,
}

/// A normalized raw media stream returned by a source adapter.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MediaFormat {
    pub format_id: String,
    pub container: String,
    pub mime_type: Option<String>,
    pub role: StreamRole,
    pub bitrate_bps: Option<u64>,
    pub content_length: Option<u64>,
    pub video: Option<VideoFormat>,
    pub audio: Option<AudioFormat>,
    pub compatible_direct_play: bool,
}

/// A user-facing quality option. It deliberately does not expose a raw provider format id.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct QualityChoice {
    pub choice_id: String,
    pub label: String,
    pub estimated_bytes: Option<u64>,
    pub video_height: Option<u32>,
    pub audio_only: bool,
    pub compatibility: Compatibility,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Compatibility {
    Preferred,
    Compatible,
    RequiresSeparateAssets,
    RequiresMuxing,
    Unsupported,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SubtitleTrack {
    pub track_id: String,
    pub format: SubtitleFormat,
    pub estimated_bytes: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MediaInfo {
    pub source: SourceIdentity,
    pub title: String,
    pub duration_ms: Option<u64>,
    pub thumbnail_url: Option<String>,
    pub formats: Vec<MediaFormat>,
    pub subtitles: Vec<SubtitleTrack>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SubtitleAssetIdentity {
    pub language: String,
    pub format: String,
    pub track_id: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LocalAsset {
    pub asset_id: String,
    pub kind: MediaKind,
    /// Portable relative path within the library root, never an Android URI.
    pub relative_path: String,
    pub bytes: u64,
    pub sha256: Option<String>,
    pub mime_type: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LibraryItem {
    pub item_id: String,
    pub source: SourceIdentity,
    pub display_title: String,
    pub duration_ms: Option<u64>,
    pub quality_label: String,
    pub assets: Vec<LocalAsset>,
    pub created_at_epoch_ms: u64,
    pub playback_position_ms: u64,
    pub completed: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DownloadPlanAsset {
    pub asset_id: String,
    pub kind: MediaKind,
    pub url: String,
    pub relative_path: String,
    pub expected_bytes: Option<u64>,
    pub expected_sha256: Option<String>,
    pub mime_type: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DownloadPlan {
    pub source: SourceIdentity,
    pub title: String,
    pub quality: QualityChoice,
    pub assets: Vec<DownloadPlanAsset>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DownloadWorkItem {
    pub job_id: String,
    pub plan: DownloadPlan,
    pub created_at_epoch_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ErrorKind {
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

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CoreError {
    pub kind: ErrorKind,
    pub message: String,
    pub retryable: bool,
}

impl CoreError {
    #[must_use]
    pub fn new(kind: ErrorKind, message: impl Into<String>, retryable: bool) -> Self {
        Self {
            kind,
            message: message.into(),
            retryable,
        }
    }
}

impl fmt::Display for CoreError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}", self.message)
    }
}

impl std::error::Error for CoreError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn curated_choice_is_provider_independent() {
        let choice = QualityChoice {
            choice_id: "best-compatible".into(),
            label: "Best compatible".into(),
            estimated_bytes: Some(42),
            video_height: Some(1080),
            audio_only: false,
            compatibility: Compatibility::Preferred,
        };
        assert_eq!(choice.choice_id, "best-compatible");
    }

    #[test]
    fn local_asset_uses_portable_relative_path() {
        let asset = LocalAsset {
            asset_id: "video".into(),
            kind: MediaKind::Video,
            relative_path: "items/abc/video.mp4".into(),
            bytes: 1,
            sha256: None,
            mime_type: Some("video/mp4".into()),
        };
        assert!(!asset.relative_path.contains("://"));
        assert!(!asset.relative_path.starts_with('/'));
    }
}
