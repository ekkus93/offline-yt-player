use crate::{validate_relative_library_path, LibraryItem, LibraryStore, MediaKind};
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryPlaybackAsset {
    pub item_id: String,
    pub video_relative_path: Option<String>,
    pub audio_relative_path: Option<String>,
    pub playable: bool,
    pub unavailable_reason: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryPlaybackAssetsResult {
    pub assets: Vec<FfiLibraryPlaybackAsset>,
    pub error_message: Option<String>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiLibraryPlaybackServiceOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, uniffi::Object)]
pub struct FfiLibraryPlaybackService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiLibraryPlaybackService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiLibraryPlaybackServiceOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiLibraryPlaybackServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn library_playback_assets(&self) -> FfiLibraryPlaybackAssetsResult {
        match self.library.list(None) {
            Ok(items) => FfiLibraryPlaybackAssetsResult {
                assets: items.iter().map(playback_asset).collect(),
                error_message: None,
            },
            Err(error) => FfiLibraryPlaybackAssetsResult {
                assets: Vec::new(),
                error_message: Some(error.message),
            },
        }
    }
}

fn playback_asset(item: &LibraryItem) -> FfiLibraryPlaybackAsset {
    if !item.completed {
        return unavailable(item, "Download is incomplete");
    }

    let video = item
        .assets
        .iter()
        .find(|asset| asset.kind == MediaKind::Video);
    let Some(video) = video else {
        return unavailable(item, "Completed item has no local video asset");
    };
    if validate_relative_library_path(&video.relative_path).is_err() {
        return unavailable(item, "Local video asset path is invalid");
    }

    let audio = item
        .assets
        .iter()
        .find(|asset| asset.kind == MediaKind::Audio);
    if let Some(audio) = audio {
        if validate_relative_library_path(&audio.relative_path).is_err() {
            return unavailable(item, "Local audio asset path is invalid");
        }
    }

    FfiLibraryPlaybackAsset {
        item_id: item.item_id.clone(),
        video_relative_path: Some(video.relative_path.clone()),
        audio_relative_path: audio.map(|asset| asset.relative_path.clone()),
        playable: true,
        unavailable_reason: None,
    }
}

fn unavailable(item: &LibraryItem, reason: &str) -> FfiLibraryPlaybackAsset {
    FfiLibraryPlaybackAsset {
        item_id: item.item_id.clone(),
        video_relative_path: None,
        audio_relative_path: None,
        playable: false,
        unavailable_reason: Some(reason.into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LocalAsset, SourceIdentity};

    fn item(completed: bool, assets: Vec<LocalAsset>) -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Fixture".into(),
            duration_ms: Some(1_000),
            quality_label: "720p".into(),
            assets,
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed,
        }
    }

    fn asset(id: &str, kind: MediaKind, path: &str) -> LocalAsset {
        LocalAsset {
            asset_id: id.into(),
            kind,
            relative_path: path.into(),
            bytes: 10,
            sha256: None,
            mime_type: None,
        }
    }

    #[test]
    fn completed_item_exposes_persisted_video_and_audio_paths() {
        let descriptor = playback_asset(&item(
            true,
            vec![
                asset("video", MediaKind::Video, "items/item-1/video.mp4"),
                asset("audio", MediaKind::Audio, "items/item-1/audio.m4a"),
            ],
        ));
        assert!(descriptor.playable);
        assert_eq!(
            descriptor.video_relative_path.as_deref(),
            Some("items/item-1/video.mp4")
        );
        assert_eq!(
            descriptor.audio_relative_path.as_deref(),
            Some("items/item-1/audio.m4a")
        );
        assert!(descriptor.unavailable_reason.is_none());
    }

    #[test]
    fn incomplete_or_missing_video_is_explicitly_unavailable() {
        let incomplete = playback_asset(&item(false, vec![]));
        assert!(!incomplete.playable);
        assert_eq!(
            incomplete.unavailable_reason.as_deref(),
            Some("Download is incomplete")
        );

        let missing = playback_asset(&item(true, vec![]));
        assert!(!missing.playable);
        assert_eq!(
            missing.unavailable_reason.as_deref(),
            Some("Completed item has no local video asset")
        );
    }

    #[test]
    fn unsafe_persisted_paths_are_never_exported_for_playback() {
        let descriptor = playback_asset(&item(
            true,
            vec![asset("video", MediaKind::Video, "../outside.mp4")],
        ));
        assert!(!descriptor.playable);
        assert!(descriptor.video_relative_path.is_none());
        assert_eq!(
            descriptor.unavailable_reason.as_deref(),
            Some("Local video asset path is invalid")
        );
    }
}
