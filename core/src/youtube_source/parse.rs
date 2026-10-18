use super::*;

pub(super) fn extract_player_json(html: &str) -> Result<Value, CoreError> {
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

pub(super) fn parse_player(v: &Value) -> Result<ExtractedYouTubeMedia, CoreError> {
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

pub(super) fn parse_subtitles(v: &Value) -> Result<Vec<ExtractedSubtitle>, CoreError> {
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

pub(super) fn parse_stream(v: &Value) -> Result<Option<ExtractedStream>, CoreError> {
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

pub(super) fn normalize_codec(c: &str) -> String {
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
