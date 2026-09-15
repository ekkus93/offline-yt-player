use crate::domain::{CoreError, ErrorKind, MediaKind, SubtitleTrack};
use crate::security::{sanitize_filename, validate_http_url, validate_relative_library_path};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OfflineAssetRequest {
    pub asset_id: String,
    pub kind: MediaKind,
    pub url: String,
    pub relative_path: String,
    pub mime_type: Option<String>,
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
        mime_type: Some(if extension == "srt" {
            "application/x-subrip"
        } else {
            "text/vtt"
        }
        .into()),
    })
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
    use crate::domain::SubtitleFormat;

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
    fn primary_metadata_is_bounded() {
        let metadata = compact_metadata(&"x".repeat(500), Some(65_000), &"q".repeat(100), 8);
        assert_eq!(metadata.title.chars().count(), 120);
        assert_eq!(metadata.duration_label.as_deref(), Some("1:05"));
        assert_eq!(metadata.quality_label.chars().count(), 40);
        assert!(metadata.detail_page_required);
    }
}
