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

mod parse;
#[cfg(test)]
mod tests;

use parse::*;

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
