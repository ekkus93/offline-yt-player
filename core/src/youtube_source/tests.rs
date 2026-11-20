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
fn split_audio_and_subtitle_track_ids_map_to_fresh_provider_neutral_assets() {
    let mut response = fixture("split");
    // Add a second video format sharing the same quality label. The highest
    // ranked compatible provider ID must be selected, not an arbitrary "1080p".
    response["streamingData"]["adaptiveFormats"]
        .as_array_mut()
        .unwrap()
        .push(serde_json::json!({
            "itag": 136,
            "url": "https://media.example/other-video.mp4",
            "mimeType": "video/mp4; codecs=\"avc1.640028\"",
            "width": 1920,
            "height": 1080,
            "bitrate": 1_000_000
        }));
    // A separate high-rate audio option must not override an explicit audio ID.
    response["streamingData"]["adaptiveFormats"]
        .as_array_mut()
        .unwrap()
        .push(serde_json::json!({
            "itag": 141,
            "url": "https://media.example/high-rate.m4a?sig=PRIVATE_SIGNED_URL",
            "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
            "bitrate": 192_000,
            "audioChannels": 2,
            "contentLength": "75000"
        }));
    response["captions"] = fixture("combined")["captions"].clone();
    let extracted = parse_player(&response).expect("sanitized split fixture");
    let url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    let media = normalize_extracted_media(url, &extracted).expect("normalize split media");
    assert_eq!(
        media.formats
            .iter()
            .find(|format| format.format_id == "140")
            .expect("AAC audio option")
            .container,
        "m4a"
    );
    assert!(
        media.formats
            .iter()
            .find(|format| format.format_id == "140")
            .unwrap()
            .compatible_direct_play
    );
    let choices = curate_quality_choices(&media);
    assert_eq!(choices.len(), 1, "duplicate 1080p labels must collapse");
    assert_eq!(choices[0].choice_id, "format:137");
    let explicit = plan_download_assets_from_fresh_media(
        &media,
        "137",
        choices[0].clone(),
        url,
        &extracted,
        Some(".en"),
        Some("140"),
    )
    .expect("selected split A/V and caption tracks");
    assert_eq!(explicit.source.media_id, "dQw4w9WgXcQ");
    assert_eq!(explicit.quality.choice_id, "format:137");
    assert_eq!(explicit.assets.len(), 3);
    assert_eq!(explicit.assets[0].asset_id, "video");
    assert_eq!(
        explicit.assets[0].relative_path,
        "items/dQw4w9WgXcQ/video.mp4"
    );
    assert_eq!(
        explicit.assets[0].url,
        "https://media.example/video.mp4"
    );
    assert_eq!(explicit.assets[1].asset_id, "audio");
    assert_eq!(
        explicit.assets[1].relative_path,
        "items/dQw4w9WgXcQ/audio.m4a"
    );
    assert_eq!(
        explicit.assets[1].url,
        "https://media.example/audio.m4a"
    );
    assert_eq!(explicit.assets[1].mime_type.as_deref(), Some("audio/mp4"));
    assert_eq!(explicit.assets[2].asset_id, "subtitle:en:.en");
    assert_eq!(
        explicit.assets[2].relative_path,
        "items/dQw4w9WgXcQ/subtitles/en.vtt"
    );
    let automatic = plan_download_assets_from_fresh_media(
        &media,
        "137",
        choices[0].clone(),
        url,
        &extracted,
        None,
        None,
    )
    .expect("best compatible automatic audio option");
    assert_eq!(automatic.assets.len(), 2);
    assert_eq!(
        automatic.assets[1].url,
        "https://media.example/high-rate.m4a?sig=PRIVATE_SIGNED_URL"
    );
    for (subtitle, audio) in [(Some("missing"), Some("140")), (None, Some("missing"))] {
        let error = plan_download_assets_from_fresh_media(
            &media,
            "137",
            choices[0].clone(),
            url,
            &extracted,
            subtitle,
            audio,
        )
        .expect_err("unrecognized selected track ID must never fall back");
        assert_eq!(error.kind, ErrorKind::NoCompatibleFormat);
        assert!(!error.message.contains("PRIVATE_SIGNED_URL"));
    }
}

#[test]
fn combined_video_rejects_inapplicable_separate_audio_selection() {
    let extracted = parse_player(&fixture("combined")).unwrap();
    let url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    let media = normalize_extracted_media(url, &extracted).unwrap();
    let choice = curate_quality_choices(&media).remove(0);
    let err = plan_download_assets_from_fresh_media(
        &media,
        "22",
        choice,
        url,
        &extracted,
        None,
        Some("140"),
    )
    .expect_err("combined A/V must not fabricate a separate audio choice");
    assert_eq!(err.kind, ErrorKind::NoCompatibleFormat);
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

/// Local loopback HTTP fixture: exercises the production watch-page network
/// path without contacting YouTube or accepting untrusted provider credentials.
fn with_local_watch_response(
    status: u16,
    body: &[u8],
    advertised_bytes: Option<usize>,
    assert_response: impl FnOnce(&YouTubeSource, &str),
) {
    use std::io::{Read as _, Write as _};
    use std::net::TcpListener;
    use std::time::Duration;

    let listener = TcpListener::bind("127.0.0.1:0").expect("local fixture socket");
    let addr = listener.local_addr().expect("local fixture address");
    let bytes = body.to_vec();
    let server = std::thread::spawn(move || {
        let (mut socket, _) = listener.accept().expect("one loopback request");
        socket
            .set_read_timeout(Some(Duration::from_secs(5)))
            .expect("set fixture timeout");
        let mut request = [0_u8; 2048];
        assert!(socket.read(&mut request).expect("read fixture request") > 0);
        let headers = format!(
            "HTTP/1.1 {status} Fixture\r\nContent-Type: text/html\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            advertised_bytes.unwrap_or(bytes.len())
        );
        socket.write_all(headers.as_bytes()).expect("write headers");
        socket.write_all(&bytes).expect("write fixture body");
    });
    assert_response(&YouTubeSource::default(), &format!("http://{addr}/watch"));
    server.join().expect("fixture thread");
}

#[test]
fn watch_page_http_status_classifies_retryability_without_leaking_body() {
    for (status, retryable) in [
        (403_u16, false),
        (404, false),
        (408, true),
        (429, true),
        (500, true),
        (502, true),
        (503, true),
        (504, true),
    ] {
        with_local_watch_response(
            status,
            b"secret_token=SHOULD_NOT_LEAK",
            None,
            |source, url| {
                let err = source.fetch_watch_page(url).unwrap_err();
                assert_eq!(err.kind, ErrorKind::HttpStatus, "status {status}");
                assert_eq!(err.retryable, retryable, "status {status}");
                assert!(!err.message.contains("SHOULD_NOT_LEAK"));
            },
        );
    }
}

#[test]
fn watch_page_with_truncated_body_returns_retryable_network_error() {
    with_local_watch_response(200, b"short", Some(999), |source, url| {
        let err = source.fetch_watch_page(url).unwrap_err();
        assert_eq!(err.kind, ErrorKind::NetworkUnavailable);
        assert!(err.retryable);
        assert!(!err.message.contains("short"));
    });
}

#[test]
fn watch_page_silent_server_times_out_without_exposing_provider_details() {
    use std::io::Read as _;
    use std::net::TcpListener;
    use std::time::Duration;

    // A local endpoint accepts the request and deliberately sends no HTTP response.
    // This exercises reqwest's actual request timeout rather than checking a
    // configured constant or requiring external YouTube network availability.
    let listener = TcpListener::bind("127.0.0.1:0").expect("test listener");
    let addr = listener.local_addr().expect("test socket address");
    let server = std::thread::spawn(move || {
        let (mut socket, _) = listener.accept().expect("test request");
        socket
            .set_read_timeout(Some(Duration::from_secs(2)))
            .expect("test socket timeout");
        let mut request = [0_u8; 2048];
        assert!(socket.read(&mut request).expect("test HTTP request") > 0);
        std::thread::sleep(Duration::from_millis(350));
    });
    let source = YouTubeSource {
        client: Client::builder()
            .connect_timeout(Duration::from_millis(100))
            .timeout(Duration::from_millis(100))
            .build()
            .expect("bounded fixture HTTP client"),
    };
    let err = source
        .fetch_watch_page(&format!("http://{addr}/watch?sig=PRIVATE_TOKEN"))
        .expect_err("silent provider must not resolve successfully");
    assert_eq!(err.kind, ErrorKind::NetworkUnavailable);
    assert!(
        err.retryable,
        "a transient provider timeout should be retryable"
    );
    assert_eq!(err.message, "YouTube request failed");
    assert!(!err.message.contains("PRIVATE_TOKEN"));
    server.join().expect("fixture socket server stopped");
}

#[test]
fn watch_page_malformed_and_valid_fixture_take_distinct_paths() {
    with_local_watch_response(
        200,
        b"<html>no player marker</html>",
        None,
        |source, url| {
            let err = source.fetch_watch_page(url).unwrap_err();
            assert_eq!(err.kind, ErrorKind::SourceChanged);
            assert!(!err.retryable);
        },
    );
    let html = format!(
        "<script>ytInitialPlayerResponse = {};</script>",
        fixture("combined")
    );
    with_local_watch_response(200, html.as_bytes(), None, |source, url| {
        let media = source
            .fetch_watch_page(url)
            .expect("valid local watch fixture");
        assert_eq!(media.title, "Sanitized Combined Fixture");
        assert_eq!(media.streams.len(), 1);
    });
}

#[test]
fn rejects_duplicate_provider_format_ids_instead_of_selecting_arbitrary_url() {
    let mut response = fixture("combined");
    let duplicate = response["streamingData"]["formats"][0].clone();
    let mut conflicting = duplicate;
    conflicting["url"] = serde_json::json!("https://media.example/unrelated.mp4");
    response["streamingData"]["adaptiveFormats"] = serde_json::json!([conflicting]);
    let err = parse_player(&response).unwrap_err();
    assert_eq!(err.kind, ErrorKind::SourceChanged);
    assert_eq!(
        err.message,
        "YouTube watch response contained duplicate format identities"
    );
}

#[test]
fn provider_field_shape_matrix_fails_closed_without_false_metadata() {
    for override_value in [
        serde_json::Value::Null,
        serde_json::json!(false),
        serde_json::json!(["not video metadata"]),
        serde_json::json!({"title": 42}),
    ] {
        let mut response = fixture("combined");
        response["videoDetails"] = override_value;
        let err = parse_player(&response).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
    }

    for variant in [
        serde_json::Value::Null,
        serde_json::json!(false),
        serde_json::json!({"formats": "not a list"}),
        serde_json::json!({"formats": [null, {"mimeType": "video/mp4"}]}),
    ] {
        let mut response = fixture("combined");
        response["streamingData"] = variant;
        let err = parse_player(&response).unwrap_err();
        assert_eq!(err.kind, ErrorKind::SourceChanged);
    }
}

#[test]
fn provider_stream_reordering_preserves_media_id_and_stream_attributes() {
    let mut response = fixture("split");
    let expected = parse_player(&response).unwrap();
    response["streamingData"]["adaptiveFormats"]
        .as_array_mut()
        .unwrap()
        .reverse();
    let actual = parse_player(&response).unwrap();

    let mut expected_formats = expected.streams;
    let mut actual_formats = actual.streams;
    expected_formats.sort_by(|a, b| a.id.cmp(&b.id));
    actual_formats.sort_by(|a, b| a.id.cmp(&b.id));
    assert_eq!(
        serde_json::to_value(&actual_formats).unwrap(),
        serde_json::to_value(&expected_formats).unwrap()
    );
    assert_eq!(actual.title, expected.title);
    assert_eq!(actual.duration_ms, expected.duration_ms);
}
