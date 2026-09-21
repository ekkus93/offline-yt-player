use crate::{
    CoreError, DownloadPlanAsset, ErrorKind, LocalAsset, MediaInfo, MediaKind,
    SubtitleAssetIdentity, SubtitleTrack,
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubtitleAssetPlan {
    pub track_id: String,
    pub language: String,
    pub format: String,
    pub relative_path: String,
    pub mime_type: String,
}

pub fn available_subtitle_tracks(media: &MediaInfo) -> Vec<SubtitleTrack> {
    media.subtitles.clone()
}

pub fn choose_subtitle_track<'a>(
    tracks: &'a [SubtitleTrack],
    requested_track_id: Option<&str>,
    preferred_language: Option<&str>,
) -> Option<&'a SubtitleTrack> {
    requested_track_id
        .and_then(|id| tracks.iter().find(|track| track.track_id == id))
        .or_else(|| {
            preferred_language.and_then(|language| {
                tracks.iter().find(|track| {
                    track.format.language.eq_ignore_ascii_case(language)
                        && !track.format.auto_generated
                })
            })
        })
        .or_else(|| tracks.iter().find(|track| !track.format.auto_generated))
        .or_else(|| tracks.first())
}

pub fn subtitle_asset_plan(
    item_id: &str,
    track: &SubtitleTrack,
) -> Result<SubtitleAssetPlan, CoreError> {
    let item_id = safe_segment(item_id, "item id")?;
    let track_id = safe_segment(&track.track_id, "subtitle track id")?;
    let language = safe_segment(&track.format.language, "subtitle language")?;
    let format = normalized_format(&track.format.format)?;
    let mime_type = match format.as_str() {
        "vtt" => "text/vtt",
        "srt" => "application/x-subrip",
        "ttml" => "application/ttml+xml",
        _ => {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "unsupported local subtitle format",
                false,
            ));
        }
    };

    Ok(SubtitleAssetPlan {
        track_id,
        language: language.clone(),
        format: format.clone(),
        relative_path: format!("items/{item_id}/subtitles/{language}.{format}"),
        mime_type: mime_type.into(),
    })
}

/// Converts a selected, freshly resolved subtitle track and URL into the same bounded,
/// provider-neutral asset model used by the download worker. Provider URLs are deliberately not
/// persisted in `LocalAsset`; only the resulting local path, language-bearing asset id, MIME, size,
/// and hash survive promotion.
pub fn subtitle_download_asset(
    item_id: &str,
    track: &SubtitleTrack,
    url: &str,
) -> Result<DownloadPlanAsset, CoreError> {
    crate::validate_http_url(url)?;
    let plan = subtitle_asset_plan(item_id, track)?;
    Ok(DownloadPlanAsset {
        asset_id: format!("subtitle:{}:{}", plan.language, plan.track_id),
        kind: MediaKind::Subtitle,
        url: url.to_owned(),
        relative_path: plan.relative_path,
        expected_bytes: track.estimated_bytes,
        expected_sha256: None,
        mime_type: Some(plan.mime_type),
    })
}

pub fn local_subtitle_assets(assets: &[LocalAsset]) -> Vec<&LocalAsset> {
    assets
        .iter()
        .filter(|asset| {
            asset.kind == MediaKind::Subtitle
                && !asset.relative_path.is_empty()
                && crate::validate_relative_library_path(&asset.relative_path).is_ok()
                && asset
                    .mime_type
                    .as_deref()
                    .and_then(subtitle_format_from_mime)
                    .is_some()
        })
        .collect()
}

pub fn local_subtitle_asset_identity(asset: &LocalAsset) -> Option<SubtitleAssetIdentity> {
    if asset.kind != MediaKind::Subtitle
        || asset.relative_path.is_empty()
        || crate::validate_relative_library_path(&asset.relative_path).is_err()
    {
        return None;
    }
    let format = asset
        .mime_type
        .as_deref()
        .and_then(subtitle_format_from_mime)?;
    let remainder = asset.asset_id.strip_prefix("subtitle:")?;
    let (language, track_id) = remainder.split_once(':')?;
    if language.is_empty() || track_id.is_empty() {
        return None;
    }
    Some(SubtitleAssetIdentity {
        language: language.into(),
        format: format.into(),
        track_id: track_id.into(),
    })
}

fn subtitle_format_from_mime(mime_type: &str) -> Option<&'static str> {
    match mime_type {
        "text/vtt" => Some("vtt"),
        "application/x-subrip" => Some("srt"),
        "application/ttml+xml" => Some("ttml"),
        _ => None,
    }
}

fn safe_segment(value: &str, label: &str) -> Result<String, CoreError> {
    let trimmed = value.trim();
    if trimmed.is_empty()
        || trimmed.contains('/')
        || trimmed.contains('\\')
        || trimmed.contains("..")
        || trimmed.contains(':')
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            format!("{label} must be a safe path segment"),
            false,
        ));
    }
    Ok(trimmed.into())
}

fn normalized_format(value: &str) -> Result<String, CoreError> {
    let format = value.trim().trim_start_matches('.').to_ascii_lowercase();
    if format.is_empty() || format.contains('/') || format.contains('\\') || format.contains("..") {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "subtitle format must be safe",
            false,
        ));
    }
    Ok(format)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{SourceIdentity, SubtitleFormat};

    fn track(id: &str, language: &str, format: &str, auto_generated: bool) -> SubtitleTrack {
        SubtitleTrack {
            track_id: id.into(),
            format: SubtitleFormat {
                language: language.into(),
                label: None,
                format: format.into(),
                auto_generated,
            },
            estimated_bytes: Some(42),
        }
    }

    #[test]
    fn enumerates_tracks_from_normalized_media_info() {
        let media = MediaInfo {
            source: SourceIdentity::new("fixture", "one"),
            title: "One".into(),
            duration_ms: None,
            thumbnail_url: None,
            formats: Vec::new(),
            subtitles: vec![track("en", "en", "vtt", false)],
        };
        assert_eq!(1, available_subtitle_tracks(&media).len());
    }

    #[test]
    fn explicit_selection_wins_then_preferred_human_track() {
        let tracks = vec![
            track("auto-en", "en", "vtt", true),
            track("human-en", "en", "vtt", false),
            track("human-es", "es", "srt", false),
        ];
        assert_eq!(
            "human-es",
            choose_subtitle_track(&tracks, Some("human-es"), Some("en"))
                .unwrap()
                .track_id
        );
        assert_eq!(
            "human-en",
            choose_subtitle_track(&tracks, None, Some("en"))
                .unwrap()
                .track_id
        );
    }

    #[test]
    fn plan_persists_language_format_and_media3_compatible_mime() {
        let plan = subtitle_asset_plan("item-1", &track("human-en", "en", "vtt", false)).unwrap();
        assert_eq!("en", plan.language);
        assert_eq!("vtt", plan.format);
        assert_eq!("text/vtt", plan.mime_type);
        assert_eq!("items/item-1/subtitles/en.vtt", plan.relative_path);
    }

    #[test]
    fn selected_track_becomes_bounded_managed_download_asset() {
        let asset = subtitle_download_asset(
            "item-1",
            &track("human-en", "en", "vtt", false),
            "https://media.example/captions/en.vtt?token=ephemeral",
        )
        .unwrap();
        assert_eq!(asset.asset_id, "subtitle:en:human-en");
        assert_eq!(asset.kind, MediaKind::Subtitle);
        assert_eq!(asset.relative_path, "items/item-1/subtitles/en.vtt");
        assert_eq!(asset.expected_bytes, Some(42));
        assert_eq!(asset.mime_type.as_deref(), Some("text/vtt"));
    }

    #[test]
    fn subtitle_download_rejects_unsafe_url_and_unsupported_format() {
        assert!(
            subtitle_download_asset(
                "item-1",
                &track("human-en", "en", "vtt", false),
                "file:///tmp/en.vtt"
            )
            .is_err()
        );
        assert!(
            subtitle_download_asset(
                "item-1",
                &track("human-en", "en", "ass", false),
                "https://media.example/en.ass"
            )
            .is_err()
        );
    }

    #[test]
    fn rejects_unsafe_or_unsupported_subtitle_paths() {
        assert!(subtitle_asset_plan("../item", &track("en", "en", "vtt", false)).is_err());
        assert!(subtitle_asset_plan("item", &track("../en", "en", "vtt", false)).is_err());
        assert!(subtitle_asset_plan("item", &track("en", "en", "ass", false)).is_err());
    }

    #[test]
    fn derives_persisted_subtitle_identity_from_asset_id_and_mime() {
        let subtitle = LocalAsset {
            asset_id: "subtitle:en:human-en".into(),
            kind: MediaKind::Subtitle,
            relative_path: "items/item/subtitles/en.vtt".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("text/vtt".into()),
        };
        let identity = local_subtitle_asset_identity(&subtitle).unwrap();
        assert_eq!(
            identity,
            SubtitleAssetIdentity {
                language: "en".into(),
                format: "vtt".into(),
                track_id: "human-en".into(),
            }
        );
    }

    #[test]
    fn local_playback_discovers_only_safe_supported_subtitle_assets() {
        let subtitle = LocalAsset {
            asset_id: "subtitle:en:human-en".into(),
            kind: MediaKind::Subtitle,
            relative_path: "items/item/subtitles/en.vtt".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("text/vtt".into()),
        };
        let unsafe_subtitle = LocalAsset {
            asset_id: "subtitle:bad".into(),
            kind: MediaKind::Subtitle,
            relative_path: "../outside.vtt".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("text/vtt".into()),
        };
        let unsupported_subtitle = LocalAsset {
            asset_id: "subtitle:ass".into(),
            kind: MediaKind::Subtitle,
            relative_path: "items/item/subtitles/en.ass".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("text/x-ssa".into()),
        };
        assert_eq!(
            vec![&subtitle],
            local_subtitle_assets(&[subtitle.clone(), unsafe_subtitle, unsupported_subtitle,])
        );
    }
}
