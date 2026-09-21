use crate::domain::{
    CoreError, ErrorKind, LibraryItem, MediaKind, SubtitleAssetIdentity, SubtitleTrack,
};
use crate::security::{sanitize_filename, validate_http_url, validate_relative_library_path};
use crate::subtitle::{local_subtitle_asset_identity, local_subtitle_assets};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OfflineAssetRequest {
    pub asset_id: String,
    pub kind: MediaKind,
    pub url: String,
    pub relative_path: String,
    pub mime_type: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OfflineSubtitleAttachment {
    pub relative_path: String,
    pub mime_type: String,
    pub identity: SubtitleAssetIdentity,
}

pub fn thumbnail_request(
    item_id: &str,
    thumbnail_url: &str,
) -> Result<OfflineAssetRequest, CoreError> {
    validate_http_url(thumbnail_url)?;
    let item = sanitize_filename(item_id);
    let relative_path = format!("items/{item}/thumbnail.jpg");
    validate_relative_library_path(&relative_path)?;
    Ok(OfflineAssetRequest {
        asset_id: "thumbnail".into(),
        kind: MediaKind::Thumbnail,
        url: thumbnail_url.into(),
        relative_path,
        mime_type: Some("image/jpeg".into()),
    })
}

pub fn subtitle_request(
    item_id: &str,
    track: &SubtitleTrack,
    url: &str,
) -> Result<OfflineAssetRequest, CoreError> {
    validate_http_url(url)?;
    let format = track.format.format.to_ascii_lowercase();
    if !matches!(format.as_str(), "vtt" | "webvtt" | "srt") {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "Unsupported offline subtitle format",
            false,
        ));
    }
    let item = sanitize_filename(item_id);
    let language = sanitize_filename(&track.format.language);
    let extension = if format == "srt" { "srt" } else { "vtt" };
    let relative_path = format!("items/{item}/subtitles/{language}.{extension}");
    validate_relative_library_path(&relative_path)?;
    Ok(OfflineAssetRequest {
        asset_id: format!("subtitle-{language}"),
        kind: MediaKind::Subtitle,
        url: url.into(),
        relative_path,
        mime_type: Some(
            if extension == "srt" {
                "application/x-subrip"
            } else {
                "text/vtt"
            }
            .into(),
        ),
    })
}

pub fn subtitle_playback_attachments(item: &LibraryItem) -> Vec<OfflineSubtitleAttachment> {
    local_subtitle_assets(&item.assets)
        .into_iter()
        .filter_map(|asset| {
            Some(OfflineSubtitleAttachment {
                relative_path: asset.relative_path.clone(),
                mime_type: asset.mime_type.clone()?,
                identity: local_subtitle_asset_identity(asset)?,
            })
        })
        .collect()
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CompactMetadata {
    pub title: String,
    pub duration_label: Option<String>,
    pub quality_label: String,
    pub detail_page_required: bool,
}

pub fn compact_metadata(
    title: &str,
    duration_ms: Option<u64>,
    quality_label: &str,
    extra_field_count: usize,
) -> CompactMetadata {
    let duration_label = duration_ms.map(|value| {
        let total_seconds = value / 1_000;
        format!("{}:{:02}", total_seconds / 60, total_seconds % 60)
    });
    CompactMetadata {
        title: title.chars().take(120).collect(),
        duration_label,
        quality_label: quality_label.chars().take(40).collect(),
        detail_page_required: extra_field_count > 3,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::{LibraryItem, LocalAsset, SourceIdentity, SubtitleFormat};

    #[test]
    fn thumbnail_is_stored_under_item_for_offline_use() {
        let request = thumbnail_request("item-1", "https://fixture.invalid/thumb.jpg").unwrap();
        assert_eq!(request.kind, MediaKind::Thumbnail);
        assert_eq!(request.relative_path, "items/item-1/thumbnail.jpg");
    }

    #[test]
    fn subtitle_preserves_language_and_supported_format() {
        let track = SubtitleTrack {
            track_id: "en".into(),
            format: SubtitleFormat {
                language: "en-US".into(),
                label: Some("English".into()),
                format: "vtt".into(),
                auto_generated: false,
            },
            estimated_bytes: Some(100),
        };
        let request = subtitle_request("item", &track, "https://fixture.invalid/en.vtt").unwrap();
        assert_eq!(request.kind, MediaKind::Subtitle);
        assert!(request.relative_path.ends_with("en-US.vtt"));
        assert_eq!(request.mime_type.as_deref(), Some("text/vtt"));
    }

    #[test]
    fn playback_attachments_include_only_safe_supported_local_subtitles() {
        let item = LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Fixture".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![
                LocalAsset {
                    asset_id: "combined".into(),
                    kind: MediaKind::Video,
                    relative_path: "items/item-1/video.mp4".into(),
                    bytes: 10,
                    sha256: None,
                    mime_type: Some("video/mp4".into()),
                },
                LocalAsset {
                    asset_id: "subtitle:en:human-en".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "items/item-1/subtitles/en.vtt".into(),
                    bytes: 5,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
                LocalAsset {
                    asset_id: "subtitle:bad".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "../outside.vtt".into(),
                    bytes: 5,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
            ],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        };

        assert_eq!(
            subtitle_playback_attachments(&item),
            vec![OfflineSubtitleAttachment {
                relative_path: "items/item-1/subtitles/en.vtt".into(),
                mime_type: "text/vtt".into(),
                identity: SubtitleAssetIdentity {
                    language: "en".into(),
                    format: "vtt".into(),
                    track_id: "human-en".into(),
                },
            }]
        );
    }

    #[test]
    fn primary_metadata_is_bounded() {
        let metadata = compact_metadata(&"x".repeat(500), Some(65_000), &"q".repeat(100), 8);
        assert_eq!(metadata.title.chars().count(), 120);
        assert_eq!(metadata.duration_label.as_deref(), Some("1:05"));
        assert_eq!(metadata.quality_label.chars().count(), 40);
        assert!(metadata.detail_page_required);
    }
}
