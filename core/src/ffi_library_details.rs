use crate::{LibraryItem, LibraryStore, MediaKind};
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryDetailAsset {
    pub asset_id: String,
    pub kind: String,
    pub relative_path: String,
    pub bytes: u64,
    pub mime_type: Option<String>,
    pub has_sha256: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryDetails {
    pub item_id: String,
    pub provider: String,
    pub media_id: String,
    pub canonical_url: Option<String>,
    pub display_title: String,
    pub duration_ms: Option<u64>,
    pub quality_label: String,
    pub completed: bool,
    pub playback_position_ms: u64,
    pub total_bytes: u64,
    pub assets: Vec<FfiLibraryDetailAsset>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryDetailsResult {
    pub details: Option<FfiLibraryDetails>,
    pub error_message: Option<String>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiLibraryDetailsServiceOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, uniffi::Object)]
pub struct FfiLibraryDetailsService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiLibraryDetailsService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiLibraryDetailsServiceOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiLibraryDetailsServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn library_details(&self, item_id: String) -> FfiLibraryDetailsResult {
        match self.library.get(&item_id) {
            Ok(Some(item)) => FfiLibraryDetailsResult {
                details: Some(details(&item)),
                error_message: None,
            },
            Ok(None) => FfiLibraryDetailsResult {
                details: None,
                error_message: Some("Library item was not found".into()),
            },
            Err(error) => FfiLibraryDetailsResult {
                details: None,
                error_message: Some(error.message),
            },
        }
    }
}

fn details(item: &LibraryItem) -> FfiLibraryDetails {
    let assets = item
        .assets
        .iter()
        .map(|asset| FfiLibraryDetailAsset {
            asset_id: asset.asset_id.clone(),
            kind: media_kind_label(asset.kind).into(),
            relative_path: asset.relative_path.clone(),
            bytes: asset.bytes,
            mime_type: asset.mime_type.clone(),
            has_sha256: asset.sha256.is_some(),
        })
        .collect::<Vec<_>>();
    FfiLibraryDetails {
        item_id: item.item_id.clone(),
        provider: item.source.provider.clone(),
        media_id: item.source.media_id.clone(),
        canonical_url: item.source.canonical_url.clone(),
        display_title: item.display_title.clone(),
        duration_ms: item.duration_ms,
        quality_label: item.quality_label.clone(),
        completed: item.completed,
        playback_position_ms: item.playback_position_ms,
        total_bytes: item.assets.iter().map(|asset| asset.bytes).sum(),
        assets,
    }
}

fn media_kind_label(kind: MediaKind) -> &'static str {
    match kind {
        MediaKind::Video => "video",
        MediaKind::Audio => "audio",
        MediaKind::Subtitle => "subtitle",
        MediaKind::Thumbnail => "thumbnail",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LocalAsset, SourceIdentity};

    #[test]
    fn details_include_source_assets_sizes_subtitles_and_integrity_metadata() {
        let item = LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity {
                provider: "youtube".into(),
                media_id: "abc".into(),
                canonical_url: Some("https://www.youtube.com/watch?v=abc".into()),
            },
            display_title: "Fixture".into(),
            duration_ms: Some(65_000),
            quality_label: "720p".into(),
            assets: vec![
                LocalAsset {
                    asset_id: "video".into(),
                    kind: MediaKind::Video,
                    relative_path: "items/item-1/video.mp4".into(),
                    bytes: 100,
                    sha256: Some("digest".into()),
                    mime_type: Some("video/mp4".into()),
                },
                LocalAsset {
                    asset_id: "subtitle:en:human".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "items/item-1/subtitles/en.vtt".into(),
                    bytes: 20,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
            ],
            created_at_epoch_ms: 1,
            playback_position_ms: 5_000,
            completed: true,
        };
        let value = details(&item);
        assert_eq!(value.provider, "youtube");
        assert_eq!(value.media_id, "abc");
        assert_eq!(value.duration_ms, Some(65_000));
        assert_eq!(value.total_bytes, 120);
        assert_eq!(value.assets.len(), 2);
        assert_eq!(value.assets[1].kind, "subtitle");
        assert!(value.assets[0].has_sha256);
        assert!(!value.assets[1].has_sha256);
    }
}
