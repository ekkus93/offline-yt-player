use crate::domain::{
    AudioFormat, Compatibility, CoreError, ErrorKind, MediaFormat, MediaInfo, QualityChoice,
    SourceIdentity, StreamRole, VideoFormat,
};
use crate::security::{sanitize_title, validate_http_url};
use crate::youtube::recognize_youtube_video_url;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedYouTubeMedia {
    pub title: String,
    pub duration_ms: Option<u64>,
    pub thumbnail_url: Option<String>,
    pub streams: Vec<ExtractedStream>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedStream {
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

pub fn normalize_extracted_media(
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
        subtitles: Vec::new(),
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

pub fn curate_quality_choices(media: &MediaInfo) -> Vec<QualityChoice> {
    let mut choices: Vec<_> = media
        .formats
        .iter()
        .filter(|format| format.video.is_some())
        .map(|format| QualityChoice {
            choice_id: format!("format:{}", format.format_id),
            label: format
                .video
                .as_ref()
                .map(|video| format!("{}p", video.height))
                .unwrap_or_else(|| "Video".into()),
            estimated_bytes: format.content_length,
            video_height: format.video.as_ref().map(|video| video.height),
            audio_only: false,
            compatibility: if format.compatible_direct_play {
                if format.role == StreamRole::Combined {
                    Compatibility::Preferred
                } else {
                    Compatibility::RequiresSeparateAssets
                }
            } else {
                Compatibility::RequiresMuxing
            },
        })
        .collect();
    choices.sort_by_key(|choice| choice.video_height);
    choices.dedup_by_key(|choice| choice.video_height);
    choices
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
        }
    }

    #[test]
    fn normalizes_metadata_and_stream_roles() {
        let media = normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &fixture()).unwrap();
        assert_eq!(media.source.media_id, "dQw4w9WgXcQ");
        assert_eq!(media.source.provider, "youtube");
        assert_eq!(media.title, "Example Video");
        assert_eq!(media.formats[0].role, StreamRole::Combined);
        assert_eq!(media.formats[1].role, StreamRole::VideoOnly);
    }

    #[test]
    fn curates_provider_independent_quality_choices() {
        let media =
            normalize_extracted_media("https://www.youtube.com/watch?v=dQw4w9WgXcQ", &fixture())
                .unwrap();
        let choices = curate_quality_choices(&media);
        assert_eq!(choices.len(), 2);
        assert_eq!(choices[0].label, "720p");
        assert_eq!(choices[0].compatibility, Compatibility::Preferred);
        assert_eq!(
            choices[1].compatibility,
            Compatibility::RequiresSeparateAssets
        );
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
