use crate::resource_bounds::{
    MAX_PROVIDER_REDIRECTS, MAX_PROVIDER_STREAMS, MAX_PROVIDER_SUBTITLE_ID_BYTES,
    MAX_PROVIDER_SUBTITLE_LABEL_CHARS, MAX_PROVIDER_SUBTITLE_LANGUAGE_BYTES,
    MAX_PROVIDER_SUBTITLES, ensure_provider_response_size, ensure_provider_url_bound,
    provider_connect_timeout, provider_request_timeout, truncate_provider_title,
};
use crate::youtube_extract::{
    ExtractedStream, ExtractedSubtitle, ExtractedYouTubeMedia, curate_quality_choices,
    normalize_extracted_media,
};
use crate::{
    Compatibility, CoreError, DownloadPlan, DownloadPlanAsset, ErrorKind, MediaInfo, MediaKind,
    MediaSource, QualityChoice, SourceFuture, StreamRole, recognize_youtube_video_url,
};
use reqwest::blocking::Client;
use serde_json::Value;

/// Narrow fail-closed production YouTube adapter described by the extraction ADR.
#[derive(Debug, Clone)]
pub struct YouTubeSource {
    client: Client,
}

impl Default for YouTubeSource {
    fn default() -> Self {
        Self {
            client: Client::builder()
                .connect_timeout(provider_connect_timeout())
                .timeout(provider_request_timeout())
                .redirect(reqwest::redirect::Policy::limited(MAX_PROVIDER_REDIRECTS))
                .build()
                .expect("static YouTube HTTP client configuration"),
        }
    }
}

impl YouTubeSource {
    fn fetch(&self, url: &str) -> Result<ExtractedYouTubeMedia, CoreError> {
        let recognized = recognize_youtube_video_url(url)?;
        let response = self
            .client
            .get(&recognized.canonical_url)
            .header("User-Agent", "Mozilla/5.0 OfflineYTPlayer/1")
            .send()
            .map_err(network_error)?;
        if !response.status().is_success() {
            return Err(CoreError::new(
                ErrorKind::HttpStatus,
                "YouTube returned a non-success HTTP status",
                response.status().is_server_error(),
            ));
        }
        ensure_provider_response_size("YouTube watch", response.content_length(), 0)?;
        let bytes = response.bytes().map_err(network_error)?;
        ensure_provider_response_size("YouTube watch", None, bytes.len())?;
        let html = std::str::from_utf8(&bytes)
            .map_err(|_| source_changed("YouTube watch response was not UTF-8"))?;
        let player = extract_player_json(html)?;
        parse_player(&player)
    }
}

impl MediaSource for YouTubeSource {
    fn id(&self) -> &'static str {
        "youtube"
    }

    fn can_handle(&self, url: &str) -> bool {
        recognize_youtube_video_url(url).is_ok()
    }

    fn resolve<'a>(&'a self, url: &'a str) -> SourceFuture<'a, MediaInfo> {
        Box::pin(async move { normalize_extracted_media(url, &self.fetch(url)?) })
    }

    fn choices<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Vec<QualityChoice>> {
        Box::pin(async move {
            let choices = curate_quality_choices(media);
            if choices.is_empty() {
                Err(CoreError::new(
                    ErrorKind::NoCompatibleFormat,
                    "YouTube returned no supported video formats",
                    false,
                ))
            } else {
                Ok(choices)
            }
        })
    }

    fn download_plan<'a>(
        &'a self,
        media: &'a MediaInfo,
        choice_id: &'a str,
    ) -> SourceFuture<'a, DownloadPlan> {
        Box::pin(async move {
            let id = choice_id.strip_prefix("format:").ok_or_else(|| {
                CoreError::new(
                    ErrorKind::NoCompatibleFormat,
                    "Unknown YouTube quality",
                    false,
                )
            })?;
            let selected = media
                .formats
                .iter()
                .find(|f| f.format_id == id)
                .ok_or_else(|| {
                    CoreError::new(
                        ErrorKind::NoCompatibleFormat,
                        "Selected YouTube format is unavailable",
                        false,
                    )
                })?;
            let choice = curate_quality_choices(media)
                .into_iter()
                .find(|c| c.choice_id == choice_id)
                .ok_or_else(|| {
                    CoreError::new(
                        ErrorKind::NoCompatibleFormat,
                        "Selected YouTube quality is unavailable",
                        false,
                    )
                })?;
            if choice.compatibility == Compatibility::RequiresMuxing {
                return Err(CoreError::new(
                    ErrorKind::NoCompatibleFormat,
                    "Selected YouTube format requires unsupported muxing",
                    false,
                ));
            }
            let canonical = media
                .source
                .canonical_url
                .as_deref()
                .ok_or_else(|| source_changed("Missing canonical YouTube URL"))?;
            let extracted = self.fetch(canonical)?;
            let fresh = normalize_extracted_media(canonical, &extracted)?;
            let video = fresh
                .formats
                .iter()
                .find(|f| f.format_id == selected.format_id)
                .ok_or_else(|| source_changed("Selected YouTube format changed during planning"))?;
            let video_stream = extracted
                .streams
                .iter()
                .find(|stream| stream.id == video.format_id)
                .ok_or_else(|| source_changed("Selected YouTube stream URL was absent"))?;
            let mut assets = vec![asset(
                video_stream,
                "video",
                MediaKind::Video,
                &media.source.media_id,
            )?];
            if video.role == StreamRole::VideoOnly {
                let audio = fresh
                    .formats
                    .iter()
                    .filter(|f| f.role == StreamRole::AudioOnly && f.compatible_direct_play)
                    .max_by_key(|f| (f.bitrate_bps.unwrap_or(0), f.format_id.clone()))
                    .ok_or_else(|| {
                        CoreError::new(
                            ErrorKind::NoCompatibleFormat,
                            "No compatible YouTube audio stream accompanies this video",
                            false,
                        )
                    })?;
                let audio_stream = extracted
                    .streams
                    .iter()
                    .find(|stream| stream.id == audio.format_id)
                    .ok_or_else(|| source_changed("Selected YouTube audio URL was absent"))?;
                assets.push(asset(
                    audio_stream,
                    "audio",
                    MediaKind::Audio,
                    &media.source.media_id,
                )?);
            }
            Ok(DownloadPlan {
                source: media.source.clone(),
                title: media.title.clone(),
                quality: choice,
                assets,
            })
        })
    }
}

fn asset(
    stream: &ExtractedStream,
    asset_id: &str,
    kind: MediaKind,
    media_id: &str,
) -> Result<DownloadPlanAsset, CoreError> {
    crate::validate_http_url(&stream.url)?;
    let extension = match stream.container.as_str() {
        "mp4" | "m4a" | "webm" => stream.container.as_str(),
        _ => "bin",
    };
    Ok(DownloadPlanAsset {
        asset_id: asset_id.to_owned(),
        kind,
        url: stream.url.clone(),
        relative_path: format!("items/{media_id}/{asset_id}.{extension}"),
        expected_bytes: stream.content_length,
        expected_sha256: None,
        mime_type: stream.mime_type.clone(),
    })
}

fn extract_player_json(html: &str) -> Result<Value, CoreError> {
    let marker = "ytInitialPlayerResponse = ";
    let start = html
        .find(marker)
        .ok_or_else(|| source_changed("YouTube player response marker was absent"))?
        + marker.len();
    let tail = &html[start..];
    let end = tail
        .find(";</script>")
        .or_else(|| tail.find(";var "))
        .ok_or_else(|| source_changed("YouTube player response terminator was absent"))?;
    serde_json::from_str(&tail[..end])
        .map_err(|_| source_changed("YouTube player response JSON was malformed"))
}

fn parse_player(v: &Value) -> Result<ExtractedYouTubeMedia, CoreError> {
    let status = v
        .pointer("/playabilityStatus/status")
        .and_then(Value::as_str)
        .unwrap_or("UNKNOWN");
    if status != "OK" {
        return Err(CoreError::new(
            ErrorKind::SourceChanged,
            "YouTube reports this media is unavailable",
            false,
        ));
    }
    let details = v
        .get("videoDetails")
        .ok_or_else(|| source_changed("YouTube video details were absent"))?;
    let title = details
        .get("title")
        .and_then(Value::as_str)
        .map(truncate_provider_title)
        .unwrap_or_else(|| "Untitled".to_owned());
    let duration_ms = details
        .get("lengthSeconds")
        .and_then(Value::as_str)
        .and_then(|s| s.parse::<u64>().ok())
        .and_then(|s| s.checked_mul(1000));
    let thumbnail_url = details
        .pointer("/thumbnail/thumbnails")
        .and_then(Value::as_array)
        .and_then(|a| a.last())
        .and_then(|x| x.get("url"))
        .and_then(Value::as_str)
        .map(str::to_owned);
    if let Some(url) = thumbnail_url.as_deref() {
        ensure_provider_url_bound("YouTube thumbnail URL", url)?;
        crate::validate_http_url(url)
            .map_err(|_| source_changed("YouTube thumbnail URL was invalid"))?;
    }
    let formats = v
        .pointer("/streamingData/formats")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .chain(
            v.pointer("/streamingData/adaptiveFormats")
                .and_then(Value::as_array)
                .into_iter()
                .flatten(),
        )
        .take(MAX_PROVIDER_STREAMS);
    let mut streams = Vec::new();
    for format in formats {
        if let Some(stream) = parse_stream(format)? {
            streams.push(stream);
        }
    }
    let subtitles = parse_subtitles(v)?;
    Ok(ExtractedYouTubeMedia {
        title,
        duration_ms,
        thumbnail_url,
        streams,
        subtitles,
    })
}

fn parse_subtitles(v: &Value) -> Result<Vec<ExtractedSubtitle>, CoreError> {
    let tracks = v
        .pointer("/captions/playerCaptionsTracklistRenderer/captionTracks")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .take(MAX_PROVIDER_SUBTITLES);
    let mut subtitles = Vec::new();
    for (index, track) in tracks.enumerate() {
        let base_url = track
            .get("baseUrl")
            .and_then(Value::as_str)
            .ok_or_else(|| source_changed("YouTube caption track URL was absent"))?;
        ensure_provider_url_bound("YouTube caption track URL", base_url)?;
        crate::validate_http_url(base_url)
            .map_err(|_| source_changed("YouTube caption track URL was invalid"))?;
        let language = track
            .get("languageCode")
            .and_then(Value::as_str)
            .filter(|s| !s.is_empty() && s.len() <= MAX_PROVIDER_SUBTITLE_LANGUAGE_BYTES)
            .ok_or_else(|| source_changed("YouTube caption language was invalid"))?;
        let label = track
            .pointer("/name/simpleText")
            .and_then(Value::as_str)
            .map(|s| s.chars().take(MAX_PROVIDER_SUBTITLE_LABEL_CHARS).collect());
        let auto_generated = track.get("kind").and_then(Value::as_str) == Some("asr");
        let id = track
            .get("vssId")
            .and_then(Value::as_str)
            .filter(|s| !s.is_empty() && s.len() <= MAX_PROVIDER_SUBTITLE_ID_BYTES)
            .map(str::to_owned)
            .unwrap_or_else(|| format!("{language}-{index}"));
        subtitles.push(ExtractedSubtitle {
            id,
            language: language.to_owned(),
            label,
            auto_generated,
        });
    }
    Ok(subtitles)
}

fn parse_stream(v: &Value) -> Result<Option<ExtractedStream>, CoreError> {
    let Some(url) = v.get("url").and_then(Value::as_str) else {
        return Ok(None);
    };
    ensure_provider_url_bound("YouTube media URL", url)?;
    let mime = v.get("mimeType").and_then(Value::as_str).unwrap_or("");
    let (mime_base, codecs) = mime.split_once(';').unwrap_or((mime, ""));
    let container = mime_base.split('/').nth(1).unwrap_or("bin").to_owned();
    let codec_list = codecs
        .split('"')
        .nth(1)
        .unwrap_or("")
        .split(',')
        .map(str::trim)
        .collect::<Vec<_>>();
    let has_video = mime_base.starts_with("video/");
    Ok(Some(ExtractedStream {
        id: v
            .get("itag")
            .and_then(Value::as_u64)
            .unwrap_or(0)
            .to_string(),
        url: url.to_owned(),
        container,
        mime_type: Some(mime_base.to_owned()),
        bitrate_bps: v.get("bitrate").and_then(Value::as_u64),
        content_length: v
            .get("contentLength")
            .and_then(Value::as_str)
            .and_then(|s| s.parse().ok()),
        width: v
            .get("width")
            .and_then(Value::as_u64)
            .and_then(|n| u32::try_from(n).ok()),
        height: v
            .get("height")
            .and_then(Value::as_u64)
            .and_then(|n| u32::try_from(n).ok()),
        fps: v
            .get("fps")
            .and_then(Value::as_u64)
            .and_then(|n| u32::try_from(n).ok()),
        video_codec: if has_video {
            codec_list.first().map(|s| normalize_codec(s))
        } else {
            None
        },
        audio_codec: if has_video {
            codec_list.get(1).map(|s| normalize_codec(s))
        } else {
            codec_list.first().map(|s| normalize_codec(s))
        },
        audio_channels: v
            .get("audioChannels")
            .and_then(Value::as_u64)
            .and_then(|n| u8::try_from(n).ok()),
    }))
}

fn normalize_codec(c: &str) -> String {
    if c.starts_with("avc1") {
        "h264"
    } else if c.starts_with("mp4a") {
        "aac"
    } else if c.starts_with("vp09") {
        "vp9"
    } else if c.starts_with("opus") {
        "opus"
    } else {
        c
    }
    .to_owned()
}

fn source_changed(message: &str) -> CoreError {
    CoreError::new(ErrorKind::SourceChanged, message, false)
}

fn network_error(_: reqwest::Error) -> CoreError {
    CoreError::new(
        ErrorKind::NetworkUnavailable,
        "YouTube request failed",
        true,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn production_adapter_recognizes_only_supported_urls() {
        let source = YouTubeSource::default();
        assert!(source.can_handle("https://youtu.be/dQw4w9WgXcQ"));
        assert!(!source.can_handle("https://example.com/watch?v=dQw4w9WgXcQ"));
    }

    #[test]
    fn fresh_stream_becomes_executable_provider_neutral_asset() {
        let stream = ExtractedStream {
            id: "22".into(),
            url: "https://media.example/video.mp4?expire=123".into(),
            container: "mp4".into(),
            mime_type: Some("video/mp4".into()),
            bitrate_bps: Some(1_000_000),
            content_length: Some(42),
            width: Some(1280),
            height: Some(720),
            fps: Some(30),
            video_codec: Some("h264".into()),
            audio_codec: Some("aac".into()),
            audio_channels: Some(2),
        };
        let planned = asset(&stream, "video", MediaKind::Video, "dQw4w9WgXcQ").unwrap();
        assert_eq!(planned.url, stream.url);
        assert_eq!(planned.relative_path, "items/dQw4w9WgXcQ/video.mp4");
        assert_eq!(planned.expected_bytes, Some(42));
        assert_eq!(planned.mime_type.as_deref(), Some("video/mp4"));
    }

    #[test]
    fn parses_manual_and_auto_generated_caption_tracks() {
        let player = serde_json::json!({
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [
                {"baseUrl": "https://www.youtube.com/api/timedtext?v=x&lang=en", "languageCode": "en", "name": {"simpleText": "English"}, "vssId": ".en"},
                {"baseUrl": "https://www.youtube.com/api/timedtext?v=x&lang=es&kind=asr", "languageCode": "es", "name": {"simpleText": "Spanish (auto-generated)"}, "vssId": "a.es", "kind": "asr"}
            ]}}
        });
        let tracks = parse_subtitles(&player).unwrap();
        assert_eq!(tracks.len(), 2);
        assert_eq!(tracks[0].language, "en");
        assert!(!tracks[0].auto_generated);
        assert_eq!(tracks[1].language, "es");
        assert!(tracks[1].auto_generated);
    }

    #[test]
    fn bounds_stream_caption_thumbnail_and_metadata_fields() {
        let long_title = "T".repeat(crate::MAX_PROVIDER_TITLE_CHARS + 16);
        let stream_formats = (0..crate::MAX_PROVIDER_STREAMS + 8)
            .map(|index| {
                serde_json::json!({
                    "itag": index + 1,
                    "url": format!("https://media.example/video-{index}.mp4"),
                    "mimeType": "video/mp4; codecs=\"avc1.42E01E, mp4a.40.2\"",
                    "width": 1280,
                    "height": 720,
                    "fps": 30,
                    "bitrate": 1_000_000,
                    "contentLength": "42"
                })
            })
            .collect::<Vec<_>>();
        let caption_tracks = (0..crate::MAX_PROVIDER_SUBTITLES + 8)
            .map(|index| {
                serde_json::json!({
                    "baseUrl": format!("https://www.youtube.com/api/timedtext?v=x&lang=en&track={index}"),
                    "languageCode": "en",
                    "name": {"simpleText": "English"},
                    "vssId": format!(".en-{index}")
                })
            })
            .collect::<Vec<_>>();
        let player = serde_json::json!({
            "playabilityStatus": {"status": "OK"},
            "videoDetails": {
                "title": long_title,
                "lengthSeconds": "60",
                "thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/example/default.jpg"}]}
            },
            "streamingData": {"formats": stream_formats},
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": caption_tracks}}
        });
        let parsed = parse_player(&player).unwrap();
        assert_eq!(parsed.title.chars().count(), crate::MAX_PROVIDER_TITLE_CHARS);
        assert_eq!(parsed.streams.len(), crate::MAX_PROVIDER_STREAMS);
        assert_eq!(parsed.subtitles.len(), crate::MAX_PROVIDER_SUBTITLES);
        assert_eq!(
            parsed.thumbnail_url.as_deref(),
            Some("https://i.ytimg.com/vi/example/default.jpg")
        );
    }

    #[test]
    fn rejects_oversized_provider_urls() {
        let oversized = format!(
            "https://media.example/{}",
            "x".repeat(crate::MAX_PROVIDER_URL_BYTES)
        );
        let stream = serde_json::json!({
            "url": oversized,
            "mimeType": "video/mp4; codecs=\"avc1.42E01E, mp4a.40.2\""
        });
        assert_eq!(
            parse_stream(&stream).unwrap_err().kind,
            ErrorKind::SourceChanged
        );

        let caption = serde_json::json!({
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [{
                "baseUrl": format!("https://www.youtube.com/api/timedtext?{}", "x".repeat(crate::MAX_PROVIDER_URL_BYTES)),
                "languageCode": "en"
            }]}}
        });
        assert_eq!(
            parse_subtitles(&caption).unwrap_err().kind,
            ErrorKind::SourceChanged
        );
    }

    #[test]
    fn rejects_invalid_or_unbounded_caption_metadata() {
        let long_language = "x".repeat(crate::MAX_PROVIDER_SUBTITLE_LANGUAGE_BYTES + 1);
        let player = serde_json::json!({
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [
                {"baseUrl": "https://www.youtube.com/api/timedtext?v=x&lang=en", "languageCode": long_language}
            ]}}
        });
        assert_eq!(
            parse_subtitles(&player).unwrap_err().kind,
            ErrorKind::SourceChanged
        );
    }

    #[test]
    #[ignore = "manual live YouTube qualification; requires OYP_LIVE_YOUTUBE_VIDEO_ID"]
    fn live_youtube_resolves_real_metadata_and_formats() {
        let video_id = std::env::var("OYP_LIVE_YOUTUBE_VIDEO_ID")
            .expect("set OYP_LIVE_YOUTUBE_VIDEO_ID to an authorized 11-character video ID");
        assert!(
            video_id.len() == 11
                && video_id
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-'),
            "live smoke accepts only a bare 11-character YouTube video ID"
        );
        let url = format!("https://www.youtube.com/watch?v={video_id}");
        let source = YouTubeSource::default();
        let media = futures::executor::block_on(source.resolve(&url))
            .expect("live YouTube metadata resolution failed");
        assert_eq!(media.source.provider, "youtube");
        assert_eq!(media.source.media_id, video_id);
        assert!(!media.title.trim().is_empty());
        assert!(!media.formats.is_empty());
        let choices = futures::executor::block_on(source.choices(&media))
            .expect("live YouTube format curation failed");
        assert!(!choices.is_empty());
    }

    #[test]
    fn rejects_malformed_caption_track() {
        let player = serde_json::json!({
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [
                {"baseUrl": "javascript:alert(1)", "languageCode": "en"}
            ]}}
        });
        assert_eq!(
            parse_subtitles(&player).unwrap_err().kind,
            ErrorKind::SourceChanged
        );
    }
}
