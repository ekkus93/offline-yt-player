use crate::domain::{
    AudioFormat, Compatibility, CoreError, ErrorKind, MediaFormat, MediaInfo, QualityChoice,
    SourceIdentity, StreamRole, SubtitleFormat, SubtitleTrack, VideoFormat,
};
use crate::security::{sanitize_title, validate_http_url};
use crate::youtube::recognize_youtube_video_url;
use serde::{Deserialize, Serialize};
use std::cmp::Reverse;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct ExtractedYouTubeMedia {
    pub title: String,
    pub duration_ms: Option<u64>,
    pub thumbnail_url: Option<String>,
    pub streams: Vec<ExtractedStream>,
    pub subtitles: Vec<ExtractedSubtitle>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct ExtractedSubtitle {
    pub id: String,
    pub language: String,
    pub label: Option<String>,
    pub auto_generated: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct ExtractedStream {
    pub id: String,
    pub url: String,
    pub container: String,
    pub mime_type: Option<String>,
    pub bitrate_bps: Option<u64>,
    pub content_length: Option<u64>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub fps: Option<u32>,
    pub video_codec: Option<String>,
    pub audio_codec: Option<String>,
    pub audio_channels: Option<u8>,
}

pub(crate) fn normalize_extracted_media(
    source_url: &str,
    extracted: &ExtractedYouTubeMedia,
) -> Result<MediaInfo, CoreError> {
    let recognized = recognize_youtube_video_url(source_url)?;
    if extracted.streams.is_empty() {
        return Err(CoreError::new(
            ErrorKind::SourceChanged,
            "YouTube extraction returned no media streams",
            false,
        ));
    }
    if let Some(thumbnail) = extracted.thumbnail_url.as_deref() {
        validate_http_url(thumbnail)?;
    }
    let formats = extracted
        .streams
        .iter()
        .map(normalize_stream)
        .collect::<Result<Vec<_>, _>>()?;
    let subtitles = extracted
        .subtitles
        .iter()
        .take(128)
        .map(|subtitle| SubtitleTrack {
            track_id: subtitle.id.clone(),
            format: SubtitleFormat {
                language: subtitle.language.clone(),
                label: subtitle.label.clone(),
                format: "vtt".into(),
                auto_generated: subtitle.auto_generated,
            },
            estimated_bytes: None,
        })
        .collect();
    Ok(MediaInfo {
        source: SourceIdentity {
            provider: "youtube".into(),
            media_id: recognized.video_id,
            canonical_url: Some(recognized.canonical_url),
        },
        title: sanitize_title(&extracted.title),
        duration_ms: extracted.duration_ms,
        thumbnail_url: extracted.thumbnail_url.clone(),
        formats,
        subtitles,
    })
}

fn normalize_stream(stream: &ExtractedStream) -> Result<MediaFormat, CoreError> {
    validate_http_url(&stream.url)?;
    let video = stream.video_codec.as_ref().map(|codec| VideoFormat {
        width: stream.width.unwrap_or(0),
        height: stream.height.unwrap_or(0),
        fps: stream.fps,
        codec: codec.clone(),
    });
    let audio = stream.audio_codec.as_ref().map(|codec| AudioFormat {
        codec: codec.clone(),
        bitrate_bps: stream.bitrate_bps,
        channels: stream.audio_channels,
        language: None,
    });
    let role = match (video.is_some(), audio.is_some()) {
        (true, true) => StreamRole::Combined,
        (true, false) => StreamRole::VideoOnly,
        (false, true) => StreamRole::AudioOnly,
        (false, false) => {
            return Err(CoreError::new(
                ErrorKind::SourceChanged,
                "YouTube extraction returned a stream without audio or video",
                false,
            ));
        }
    };
    let compatible_direct_play = matches!(
        (
            stream.container.as_str(),
            stream.video_codec.as_deref(),
            stream.audio_codec.as_deref()
        ),
        ("mp4", Some("h264"), Some("aac"))
            | ("mp4", Some("h264"), None)
            | ("m4a", None, Some("aac"))
            | ("webm", Some("vp9"), None)
            | ("webm", None, Some("opus"))
    );
    Ok(MediaFormat {
        format_id: stream.id.clone(),
        container: stream.container.clone(),
        mime_type: stream.mime_type.clone(),
        role,
        bitrate_bps: stream.bitrate_bps,
        content_length: stream.content_length,
        video,
        audio,
        compatible_direct_play,
    })
}

pub(crate) fn curate_quality_choices(media: &MediaInfo) -> Vec<QualityChoice> {
    let mut formats = media
        .formats
        .iter()
        .filter(|format| format.video.is_some())
        .collect::<Vec<_>>();
    formats.sort_by_key(|format| format_rank(format));
    formats.dedup_by_key(|format| format.video.as_ref().map(|video| video.height));
    formats.into_iter().map(quality_choice).collect()
}

fn quality_choice(format: &MediaFormat) -> QualityChoice {
    QualityChoice {
        choice_id: format!("format:{}", format.format_id),
        label: format
            .video
            .as_ref()
            .map(|video| format!("{}p", video.height))
            .unwrap_or_else(|| "Video".into()),
        estimated_bytes: format.content_length,
        video_height: format.video.as_ref().map(|video| video.height),
        audio_only: false,
        compatibility: format_compatibility(format),
    }
}

fn format_compatibility(format: &MediaFormat) -> Compatibility {
    if format.compatible_direct_play {
        if format.role == StreamRole::Combined {
            Compatibility::Preferred
        } else {
            Compatibility::RequiresSeparateAssets
        }
    } else {
        Compatibility::RequiresMuxing
    }
}

fn format_rank(
    format: &MediaFormat,
) -> (
    Reverse<u32>,
    u8,
    u8,
    Reverse<u64>,
    Reverse<u32>,
    String,
    String,
    String,
) {
    let video = format.video.as_ref();
    (
        Reverse(video.map_or(0, |video| video.height)),
        compatibility_rank(format_compatibility(format)),
        role_rank(format.role),
        Reverse(format.bitrate_bps.unwrap_or(0)),
        Reverse(video.and_then(|video| video.fps).unwrap_or(0)),
        video.map_or_else(String::new, |video| video.codec.clone()),
        format.container.clone(),
        format.format_id.clone(),
    )
}

fn compatibility_rank(value: Compatibility) -> u8 {
    match value {
        Compatibility::Preferred => 0,
        Compatibility::Compatible | Compatibility::RequiresSeparateAssets => 1,
        Compatibility::RequiresMuxing => 2,
        Compatibility::Unsupported => 3,
    }
}

fn role_rank(value: StreamRole) -> u8 {
    match value {
        StreamRole::Combined => 0,
        StreamRole::VideoOnly => 1,
        StreamRole::AudioOnly => 2,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> ExtractedYouTubeMedia {
        ExtractedYouTubeMedia {
            title: "  Example\nVideo  ".into(),
            duration_ms: Some(12_345),
            thumbnail_url: Some("https://i.ytimg.com/example.jpg".into()),
            streams: vec![
                ExtractedStream {
                    id: "22".into(),
                    url: "https://media.example/combined.mp4".into(),
                    container: "mp4".into(),
                    mime_type: Some("video/mp4".into()),
                    bitrate_bps: Some(1_500_000),
                    content_length: Some(2_000_000),
                    width: Some(1280),
                    height: Some(720),
                    fps: Some(30),
                    video_codec: Some("h264".into()),
                    audio_codec: Some("aac".into()),
                    audio_channels: Some(2),
                },
                ExtractedStream {
                    id: "137".into(),
                    url: "https://media.example/video.mp4".into(),
                    container: "mp4".into(),
                    mime_type: Some("video/mp4".into()),
                    bitrate_bps: Some(3_000_000),
                    content_length: Some(4_000_000),
                    width: Some(1920),
                    height: Some(1080),
                    fps: Some(30),
                    video_codec: Some("h264".into()),
                    audio_codec: None,
                    audio_channels: None,
                },
            ],
            subtitles: vec![ExtractedSubtitle {
                id: "en".into(),
                language: "en".into(),
                label: Some("English".into()),
                auto_generated: false,
            }],
        }
    }

    fn stream(
        id: &str,
        height: u32,
        role: StreamRole,
        compatible: bool,
        bitrate: u64,
        fps: u32,
        codec: &str,
    ) -> MediaFormat {
        MediaFormat {
            format_id: id.into(),
            container: if compatible { "mp4" } else { "webm" }.into(),
            mime_type: Some("video/mp4".into()),
            role,
            bitrate_bps: Some(bitrate),
            content_length: Some(bitrate / 8),
            video: Some(VideoFormat {
                width: height.saturating_mul(16) / 9,
                height,
                fps: Some(fps),
                codec: codec.into(),
            }),
            audio: (role == StreamRole::Combined).then(|| AudioFormat {
                codec: "aac".into(),
                bitrate_bps: Some(128_000),
                channels: Some(2),
                language: None,
            }),
            compatible_direct_play: compatible,
        }
    }

    fn media_with_formats(formats: Vec<MediaFormat>) -> MediaInfo {
        MediaInfo {
            source: SourceIdentity::new("youtube", "dQw4w9WgXcQ"),
            title: "Example".into(),
            duration_ms: Some(1_000),
            thumbnail_url: None,
            formats,
            subtitles: Vec::new(),
        }
    }

    #[test]
    fn normalizes_metadata_stream_roles_and_subtitles() {
        let media = normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &fixture()).unwrap();
        assert_eq!(media.source.media_id, "dQw4w9WgXcQ");
        assert_eq!(media.source.provider, "youtube");
        assert_eq!(media.title, "Example Video");
        assert_eq!(media.formats[0].role, StreamRole::Combined);
        assert_eq!(media.formats[1].role, StreamRole::VideoOnly);
        assert_eq!(media.subtitles.len(), 1);
        assert_eq!(media.subtitles[0].format.language, "en");
        assert_eq!(media.subtitles[0].format.label.as_deref(), Some("English"));
    }

    #[test]
    fn curates_provider_independent_quality_choices() {
        let media =
            normalize_extracted_media("https://www.youtube.com/watch?v=dQw4w9WgXcQ", &fixture())
                .unwrap();
        let choices = curate_quality_choices(&media);
        assert_eq!(choices.len(), 2);
        assert_eq!(choices[0].label, "1080p");
        assert_eq!(
            choices[0].compatibility,
            Compatibility::RequiresSeparateAssets
        );
        assert_eq!(choices[1].label, "720p");
        assert_eq!(choices[1].compatibility, Compatibility::Preferred);
    }

    #[test]
    fn quality_dedup_prefers_direct_combined_even_when_provider_order_is_adversarial() {
        let media = media_with_formats(vec![
            stream(
                "mux-first",
                720,
                StreamRole::VideoOnly,
                false,
                3_000_000,
                30,
                "av1",
            ),
            stream(
                "split-second",
                720,
                StreamRole::VideoOnly,
                true,
                2_500_000,
                30,
                "h264",
            ),
            stream(
                "combined-last",
                720,
                StreamRole::Combined,
                true,
                1_500_000,
                30,
                "h264",
            ),
        ]);

        let choices = curate_quality_choices(&media);

        assert_eq!(choices.len(), 1);
        assert_eq!(choices[0].choice_id, "format:combined-last");
        assert_eq!(choices[0].compatibility, Compatibility::Preferred);
    }

    #[test]
    fn quality_dedup_prefers_compatible_split_over_mux_required() {
        let media = media_with_formats(vec![
            stream(
                "mux-first",
                1080,
                StreamRole::VideoOnly,
                false,
                5_000_000,
                60,
                "av1",
            ),
            stream(
                "split-second",
                1080,
                StreamRole::VideoOnly,
                true,
                3_500_000,
                30,
                "h264",
            ),
        ]);

        let choices = curate_quality_choices(&media);

        assert_eq!(choices.len(), 1);
        assert_eq!(choices[0].choice_id, "format:split-second");
        assert_eq!(
            choices[0].compatibility,
            Compatibility::RequiresSeparateAssets
        );
    }

    #[test]
    fn quality_ranking_has_deterministic_tie_breakers() {
        let media = media_with_formats(vec![
            stream(
                "id-b",
                720,
                StreamRole::VideoOnly,
                true,
                2_500_000,
                30,
                "h264",
            ),
            stream(
                "id-a",
                720,
                StreamRole::VideoOnly,
                true,
                2_500_000,
                30,
                "h264",
            ),
            stream(
                "id-high-fps",
                720,
                StreamRole::VideoOnly,
                true,
                2_500_000,
                60,
                "h264",
            ),
            stream(
                "id-high-bitrate",
                720,
                StreamRole::VideoOnly,
                true,
                3_000_000,
                30,
                "h264",
            ),
        ]);

        let choices = curate_quality_choices(&media);

        assert_eq!(choices.len(), 1);
        assert_eq!(choices[0].choice_id, "format:id-high-bitrate");
    }

    #[test]
    fn signed_provider_stream_urls_do_not_cross_normalized_or_ffi_boundary() {
        let mut extracted = fixture();
        extracted.streams[0].url =
            "https://rr.example/videoplayback?sig=SECRET_TOKEN&expire=999".into();
        let media =
            normalize_extracted_media("https://www.youtube.com/watch?v=dQw4w9WgXcQ", &extracted)
                .unwrap();

        let normalized_debug = format!("{media:?}");
        assert!(!normalized_debug.contains("SECRET_TOKEN"));
        assert!(!normalized_debug.contains("videoplayback"));

        let summary = crate::FfiMediaSummary::from(&media);
        let ffi_debug = format!("{summary:?}");
        assert!(!ffi_debug.contains("SECRET_TOKEN"));
        assert!(!ffi_debug.contains("videoplayback"));
    }

    #[test]
    fn rejects_empty_or_malformed_extraction() {
        let mut extracted = fixture();
        extracted.streams.clear();
        assert_eq!(
            normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &extracted)
                .unwrap_err()
                .kind,
            ErrorKind::SourceChanged
        );
        let mut extracted = fixture();
        extracted.streams[0].url = "javascript:alert(1)".into();
        assert_eq!(
            normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &extracted)
                .unwrap_err()
                .kind,
            ErrorKind::InvalidInput
        );
    }
}
