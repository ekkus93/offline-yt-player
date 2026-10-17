use crate::resource_bounds::{
    MAX_PROVIDER_REDIRECTS, MAX_PROVIDER_RESPONSE_BYTES, MAX_PROVIDER_STREAMS,
    MAX_PROVIDER_SUBTITLE_ID_BYTES, MAX_PROVIDER_SUBTITLE_LABEL_CHARS,
    MAX_PROVIDER_SUBTITLE_LANGUAGE_BYTES, MAX_PROVIDER_SUBTITLES, ensure_provider_response_size,
    ensure_provider_url_bound, provider_connect_timeout, provider_request_timeout,
    truncate_provider_title,
};
use crate::youtube_extract::{
    ExtractedStream, ExtractedSubtitle, ExtractedYouTubeMedia, curate_quality_choices,
    normalize_extracted_media,
};
use crate::{
    Compatibility, CoreError, DownloadPlan, DownloadPlanAsset, ErrorKind, MediaInfo, MediaKind,
    MediaSource, QualityChoice, SourceFuture, StreamRole, recognize_youtube_video_url,
    subtitle_download_asset,
};
use reqwest::blocking::Client;
use serde_json::Value;
use std::io::Read;

const INNERTUBE_PLAYER_URL: &str = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
const INNERTUBE_ANDROID_CLIENT_NUMBER: &str = "3";
const INNERTUBE_ANDROID_CLIENT_VERSION: &str = "21.26.364";
const INNERTUBE_ANDROID_USER_AGENT: &str =
    "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip";

// The request body for the Innertube `android` client. It carries only the public client
// identity and the video ID: no cookies, tokens, or credentials.
fn innertube_android_request(video_id: &str) -> String {
    serde_json::json!({
        "context": {"client": {
            "clientName": "ANDROID",
            "clientVersion": INNERTUBE_ANDROID_CLIENT_VERSION,
            "androidSdkVersion": 30,
            "osName": "Android",
            "osVersion": "11",
            "userAgent": INNERTUBE_ANDROID_USER_AGENT,
            "hl": "en",
            "gl": "US"
        }},
        "videoId": video_id,
        "contentCheckOk": true,
        "racyCheckOk": true
    })
    .to_string()
}

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
        let watch = self.fetch_watch_page(&recognized.canonical_url);
        match watch {
            // The watch page can answer with sign-in gating or with only
            // `serverAbrStreamingUrl`. Both leave nothing the downloader can fetch, so ask
            // the Innertube player endpoint as the Android app does. Report the original
            // watch-page error if that also fails.
            Err(error) if error.kind == ErrorKind::SourceChanged => self
                .fetch_innertube_android(&recognized.video_id)
                .map_err(|_| error),
            other => other,
        }
    }

    fn fetch_innertube_android(&self, video_id: &str) -> Result<ExtractedYouTubeMedia, CoreError> {
        let response = self
            .client
            .post(INNERTUBE_PLAYER_URL)
            .header("Content-Type", "application/json")
            .header("User-Agent", INNERTUBE_ANDROID_USER_AGENT)
            .header("X-YouTube-Client-Name", INNERTUBE_ANDROID_CLIENT_NUMBER)
            .header("X-YouTube-Client-Version", INNERTUBE_ANDROID_CLIENT_VERSION)
            .body(innertube_android_request(video_id))
            .send()
            .map_err(network_error)?;
        if !response.status().is_success() {
            return Err(CoreError::new(
                ErrorKind::HttpStatus,
                "YouTube player endpoint returned a non-success HTTP status",
                response.status().is_server_error(),
            ));
        }
        ensure_provider_response_size("YouTube player", response.content_length(), 0)?;
        let bytes = read_bounded_watch_body(response)?;
        let player = serde_json::from_slice(&bytes)
            .map_err(|_| source_changed("YouTube player endpoint response was malformed"))?;
        parse_player(&player)
    }

    fn fetch_watch_page(&self, canonical_url: &str) -> Result<ExtractedYouTubeMedia, CoreError> {
        let response = self
            .client
            .get(canonical_url)
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
        let bytes = read_bounded_watch_body(response)?;
        let html = std::str::from_utf8(&bytes)
            .map_err(|_| source_changed("YouTube watch response was not UTF-8"))?;
        let player = extract_player_json(html)?;
        parse_player(&player)
    }
}

// A declared Content-Length may be absent or dishonest. Stop reading after one byte
// beyond the metadata bound; do not buffer an arbitrarily large chunked response
// and only then check its size.
fn read_bounded_watch_body(reader: impl Read) -> Result<Vec<u8>, CoreError> {
    let mut bounded = reader.take(MAX_PROVIDER_RESPONSE_BYTES as u64 + 1);
    let mut bytes = Vec::new();
    bounded.read_to_end(&mut bytes).map_err(|_| {
        CoreError::new(
            ErrorKind::NetworkUnavailable,
            "YouTube watch response read failed",
            true,
        )
    })?;
    ensure_provider_response_size("YouTube watch", None, bytes.len())?;
    Ok(bytes)
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
        self.download_plan_with_options(media, choice_id, None, None)
    }

    fn download_plan_with_options<'a>(
        &'a self,
        media: &'a MediaInfo,
        choice_id: &'a str,
        subtitle_track_id: Option<&'a str>,
        audio_format_id: Option<&'a str>,
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
                let audio = match audio_format_id {
                    Some(requested) => fresh
                        .formats
                        .iter()
                        .find(|format| {
                            format.format_id == requested
                                && format.role == StreamRole::AudioOnly
                                && format.compatible_direct_play
                        })
                        .ok_or_else(|| {
                            CoreError::new(
                                ErrorKind::NoCompatibleFormat,
                                "Selected YouTube audio track is unavailable",
                                false,
                            )
                        })?,
                    None => fresh
                        .formats
                        .iter()
                        .filter(|format| {
                            format.role == StreamRole::AudioOnly && format.compatible_direct_play
                        })
                        .max_by_key(|format| {
                            (format.bitrate_bps.unwrap_or(0), format.format_id.clone())
                        })
                        .ok_or_else(|| {
                            CoreError::new(
                                ErrorKind::NoCompatibleFormat,
                                "No compatible YouTube audio stream accompanies this video",
                                false,
                            )
                        })?,
                };
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
            } else if audio_format_id.is_some() {
                return Err(CoreError::new(
                    ErrorKind::NoCompatibleFormat,
                    "Separate audio selection is not applicable to the selected combined format",
                    false,
                ));
            }

            if let Some(requested_track_id) = subtitle_track_id {
                let track = fresh
                    .subtitles
                    .iter()
                    .find(|track| track.track_id == requested_track_id)
                    .ok_or_else(|| {
                        CoreError::new(
                            ErrorKind::NoCompatibleFormat,
                            "Selected YouTube subtitle track is unavailable",
                            false,
                        )
                    })?;
                let extracted_track = extracted
                    .subtitles
                    .iter()
                    .find(|track| track.id == requested_track_id)
                    .ok_or_else(|| source_changed("Selected YouTube subtitle URL was absent"))?;
                assets.push(subtitle_download_asset(
                    &media.source.media_id,
                    track,
                    &extracted_track.url,
                )?);
            }

            Ok(DownloadPlan {
                source: media.source.clone(),
                title: media.title.clone(),
                duration_ms: media.duration_ms,
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
        let message = match status {
            "LOGIN_REQUIRED" => "YouTube requires sign-in for this request",
            "ERROR" | "UNPLAYABLE" | "LIVE_STREAM_OFFLINE" => {
                "YouTube did not make this media playable to this request"
            }
            _ => "YouTube returned an unrecognized playability status",
        };
        return Err(source_changed(message));
    }
    let details = v
        .get("videoDetails")
        .ok_or_else(|| source_changed("YouTube video details were absent"))?;
    let title = details
        .get("title")
        .and_then(Value::as_str)
        .filter(|title| {
            title
                .chars()
                .any(|character| !character.is_control() && !character.is_whitespace())
        })
        .map(truncate_provider_title)
        .ok_or_else(|| source_changed("YouTube video title was absent or blank"))?;
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
    if streams.is_empty() {
        return Err(source_changed(
            "YouTube watch response exposed no direct media URLs",
        ));
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
    for track in tracks {
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
            .ok_or_else(|| source_changed("YouTube caption track identity was invalid"))?;
        subtitles.push(ExtractedSubtitle {
            id,
            url: base_url.to_owned(),
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
        .filter(|codec| !codec.is_empty())
        .collect::<Vec<_>>();
    let has_video = mime_base.starts_with("video/");
    let id = v
        .get("itag")
        .and_then(Value::as_u64)
        .filter(|id| *id > 0)
        .ok_or_else(|| source_changed("YouTube stream identity was absent or invalid"))?;
    Ok(Some(ExtractedStream {
        id: id.to_string(),
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
        assert_eq!(
            tracks[0].url,
            "https://www.youtube.com/api/timedtext?v=x&lang=en"
        );
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
        assert_eq!(
            parsed.title.chars().count(),
            crate::MAX_PROVIDER_TITLE_CHARS
        );
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

    fn fixture(name: &str) -> Value {
        let text = match name {
            "combined" => include_str!("../tests/fixtures/youtube/combined_av.json"),
            "split" => include_str!("../tests/fixtures/youtube/split_av.json"),
            "unavailable" => include_str!("../tests/fixtures/youtube/provider_unavailable.json"),
            _ => panic!("unknown fixture"),
        };
        serde_json::from_str(text).expect("sanitized YouTube fixture must be valid JSON")
    }

    #[test]
    fn sanitized_combined_av_fixture_extracts_metadata_and_format() {
        let parsed = parse_player(&fixture("combined")).unwrap();
        assert_eq!(parsed.title, "Sanitized Combined Fixture");
        assert_eq!(parsed.duration_ms, Some(123_000));
        assert_eq!(parsed.streams.len(), 1);
        assert_eq!(parsed.streams[0].id, "22");
        assert_eq!(parsed.streams[0].video_codec.as_deref(), Some("h264"));
        assert_eq!(parsed.streams[0].audio_codec.as_deref(), Some("aac"));
        assert_eq!(parsed.subtitles.len(), 1);
        assert_eq!(parsed.subtitles[0].language, "en");
    }

    #[test]
    fn sanitized_split_av_fixture_extracts_separate_video_and_audio() {
        let parsed = parse_player(&fixture("split")).unwrap();
        assert_eq!(parsed.title, "Sanitized Split Fixture");
        assert_eq!(parsed.streams.len(), 2);
        let has_video_only = parsed.streams.iter().any(|s| {
            s.id == "137" && s.video_codec.as_deref() == Some("h264") && s.audio_codec.is_none()
        });
        let has_audio_only = parsed.streams.iter().any(|s| {
            s.id == "140" && s.video_codec.is_none() && s.audio_codec.as_deref() == Some("aac")
        });
        assert!(has_video_only);
        assert!(has_audio_only);
    }

    #[test]
    fn sanitized_unavailable_fixture_fails_closed() {
        let err = parse_player(&fixture("unavailable")).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
        assert_eq!(
            crate::youtube_diagnostics::youtube_diagnostic(&err).code,
            "youtube.playback_rejected"
        );
    }

    #[test]
    fn login_required_is_not_reported_as_video_unavailable() {
        let player = serde_json::json!({
            "playabilityStatus": {"status": "LOGIN_REQUIRED", "reason": "untrusted provider text"}
        });
        let err = parse_player(&player).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
        assert_eq!(err.message, "YouTube requires sign-in for this request");
        let diagnostic = crate::youtube_diagnostics::youtube_diagnostic(&err);
        assert_eq!(diagnostic.code, "youtube.login_required");
        assert!(!diagnostic.user_message.contains("untrusted provider text"));
    }

    #[test]
    fn sabr_only_response_does_not_fabricate_downloadable_formats() {
        let player = serde_json::json!({
            "playabilityStatus": {"status": "OK"},
            "videoDetails": {"title": "Fixture", "lengthSeconds": "60"},
            "streamingData": {
                "adaptiveFormats": [{"itag": 137, "mimeType": "video/mp4", "width": 1280, "height": 720}],
                "serverAbrStreamingUrl": "https://media.example/sabr?sig=SECRET_TOKEN"
            }
        });
        let err = parse_player(&player).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
        assert_eq!(
            err.message,
            "YouTube watch response exposed no direct media URLs"
        );
        assert!(!err.message.contains("SECRET_TOKEN"));
    }

    #[test]
    fn malformed_watch_fixture_is_rejected() {
        let html = include_str!("../tests/fixtures/youtube/malformed_watch.html");
        let err = extract_player_json(html).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
    }

    #[test]
    fn unknown_length_watch_response_is_bounded_during_streaming() {
        // An infinite reader models chunked input with no Content-Length. This
        // must terminate at MAX_PROVIDER_RESPONSE_BYTES + 1, not buffer endlessly.
        let err = read_bounded_watch_body(std::io::repeat(b'x')).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
        assert_eq!(
            read_bounded_watch_body(std::io::repeat(b'x').take(MAX_PROVIDER_RESPONSE_BYTES as u64))
                .unwrap()
                .len(),
            MAX_PROVIDER_RESPONSE_BYTES
        );
    }

    #[test]
    fn oversized_watch_response_is_rejected_before_parsing() {
        let oversized = crate::MAX_PROVIDER_RESPONSE_BYTES + 1;
        let err =
            ensure_provider_response_size("YouTube watch", Some(oversized as u64), 0).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
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
        let media = match futures::executor::block_on(source.resolve(&url)) {
            Ok(media) => media,
            Err(error) => {
                // Sanitized codes only: never provider text, URLs, or tokens.
                let innertube = source
                    .fetch_innertube_android(&video_id)
                    .err()
                    .map_or("ok", |e| {
                        crate::youtube_diagnostics::youtube_diagnostic(&e).code
                    });
                panic!(
                    "live YouTube metadata resolution failed: {} (innertube android: {innertube})",
                    crate::youtube_diagnostics::youtube_diagnostic(&error).code
                );
            }
        };
        assert_eq!(media.source.provider, "youtube");
        assert_eq!(media.source.media_id, video_id);
        assert!(!media.title.trim().is_empty());
        assert!(!media.formats.is_empty());
        let choices = futures::executor::block_on(source.choices(&media))
            .expect("live YouTube format curation failed");
        assert!(!choices.is_empty());
        let plan = futures::executor::block_on(source.download_plan(&media, &choices[0].choice_id))
            .expect("live YouTube download plan failed");
        assert_eq!(plan.source.media_id, video_id);
        assert!(
            plan.assets
                .iter()
                .any(|asset| asset.kind == MediaKind::Video && !asset.url.is_empty()),
            "live plan must contain an executable video asset"
        );
    }

    #[test]
    fn innertube_android_request_names_only_the_video_and_public_client() {
        let body: Value =
            serde_json::from_str(&innertube_android_request("QLnP7oK--5Y")).expect("valid JSON");
        assert_eq!(body["videoId"], "QLnP7oK--5Y");
        assert_eq!(body["context"]["client"]["clientName"], "ANDROID");
        let keys: Vec<_> = body.as_object().expect("object").keys().cloned().collect();
        assert!(
            keys.iter().all(|key| matches!(
                key.as_str(),
                "context" | "videoId" | "contentCheckOk" | "racyCheckOk"
            )),
            "request must not carry cookies, tokens, or credentials: {keys:?}"
        );
    }

    #[test]
    fn innertube_android_response_with_direct_combined_stream_resolves() {
        let player = serde_json::json!({
            "playabilityStatus": {"status": "OK"},
            "videoDetails": {
                "title": "Sample",
                "lengthSeconds": "212",
                "thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/example/default.jpg"}]}
            },
            "streamingData": {
                "formats": [{
                    "itag": 18,
                    "url": "https://media.example/videoplayback?itag=18",
                    "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                    "contentLength": "5820740",
                    "bitrate": 400000,
                    "width": 640,
                    "height": 360
                }],
                "adaptiveFormats": [{"itag": 137, "mimeType": "video/mp4; codecs=\"avc1.640028\""}],
                "serverAbrStreamingUrl": "https://media.example/sabr?sig=SECRET_TOKEN"
            }
        });
        let media = parse_player(&player).expect("direct combined stream resolves");
        assert_eq!(media.streams.len(), 1);
        assert_eq!(media.streams[0].id, "18");
        assert_eq!(media.streams[0].audio_codec.as_deref(), Some("aac"));
        assert_eq!(media.streams[0].video_codec.as_deref(), Some("h264"));
    }

    #[test]
    fn missing_or_blank_provider_title_fails_closed() {
        for title in [None, Some("   \n\t")] {
            let mut details = serde_json::json!({
                "lengthSeconds": "60",
                "thumbnail": {"thumbnails": []}
            });
            if let Some(title) = title {
                details["title"] = serde_json::Value::String(title.to_owned());
            }
            let player = serde_json::json!({
                "playabilityStatus": {"status": "OK"},
                "videoDetails": details,
                "streamingData": {"formats": []}
            });
            assert_eq!(
                parse_player(&player).unwrap_err().kind,
                ErrorKind::SourceChanged
            );
        }
    }

    #[test]
    fn missing_stream_identity_fails_closed_instead_of_fabricating_zero() {
        let stream = serde_json::json!({
            "url": "https://media.example/video.mp4",
            "mimeType": "video/mp4; codecs=\"avc1.42E01E, mp4a.40.2\"",
            "width": 1280,
            "height": 720
        });
        assert_eq!(
            parse_stream(&stream).unwrap_err().kind,
            ErrorKind::SourceChanged
        );
    }

    #[test]
    fn missing_subtitle_identity_fails_closed_instead_of_fabricating_track_id() {
        let player = serde_json::json!({
            "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [
                {
                    "baseUrl": "https://www.youtube.com/api/timedtext?v=x&lang=en",
                    "languageCode": "en"
                }
            ]}}
        });
        assert_eq!(
            parse_subtitles(&player).unwrap_err().kind,
            ErrorKind::SourceChanged
        );
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
