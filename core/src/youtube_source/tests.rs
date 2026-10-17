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
        "combined" => include_str!("../../tests/fixtures/youtube/combined_av.json"),
        "split" => include_str!("../../tests/fixtures/youtube/split_av.json"),
        "unavailable" => include_str!("../../tests/fixtures/youtube/provider_unavailable.json"),
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
    let html = include_str!("../../tests/fixtures/youtube/malformed_watch.html");
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
