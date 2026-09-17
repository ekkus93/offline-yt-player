use crate::{CoreError, ErrorKind, LocalAsset, MediaInfo, MediaKind, SubtitleTrack};

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

pub fn local_subtitle_assets(assets: &[LocalAsset]) -> Vec<&LocalAsset> {
    assets
        .iter()
        .filter(|asset| asset.kind == MediaKind::Subtitle && !asset.relative_path.is_empty())
        .collect()
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
    fn rejects_unsafe_or_unsupported_subtitle_paths() {
        assert!(subtitle_asset_plan("../item", &track("en", "en", "vtt", false)).is_err());
        assert!(subtitle_asset_plan("item", &track("../en", "en", "vtt", false)).is_err());
        assert!(subtitle_asset_plan("item", &track("en", "en", "ass", false)).is_err());
    }

    #[test]
    fn local_playback_discovers_only_subtitle_assets() {
        let subtitle = LocalAsset {
            asset_id: "subtitle-en".into(),
            kind: MediaKind::Subtitle,
            relative_path: "items/item/subtitles/en.vtt".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("text/vtt".into()),
        };
        let video = LocalAsset {
            asset_id: "video".into(),
            kind: MediaKind::Video,
            relative_path: "items/item/video.mp4".into(),
            bytes: 100,
            sha256: None,
            mime_type: Some("video/mp4".into()),
        };
        assert_eq!(
            vec![&subtitle],
            local_subtitle_assets(&[subtitle.clone(), video])
        );
    }
}
