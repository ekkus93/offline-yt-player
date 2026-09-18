use crate::youtube_source::parse_player;
use crate::{ErrorKind, StreamRole};
use serde_json::Value;

fn fixture(name: &str) -> Value {
    let text = match name {
        "combined" => include_str!("../tests/fixtures/youtube/combined.json"),
        "split" => include_str!("../tests/fixtures/youtube/split.json"),
        "unavailable" => include_str!("../tests/fixtures/youtube/unavailable.json"),
        "malformed" => include_str!("../tests/fixtures/youtube/malformed.json"),
        _ => panic!("unknown fixture"),
    };
    serde_json::from_str(text).expect("sanitized fixture must be valid JSON")
}

#[test]
fn combined_fixture_extracts_metadata_and_combined_stream() {
    let media = parse_player(&fixture("combined")).unwrap();
    assert_eq!(media.title, "Fixture Combined");
    assert_eq!(media.duration_ms, Some(42_000));
    assert_eq!(
        media.thumbnail_url.as_deref(),
        Some("https://i.example/combined.jpg")
    );
    assert_eq!(media.streams.len(), 1);
    let normalized =
        crate::normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &media).unwrap();
    assert_eq!(normalized.formats[0].role, StreamRole::Combined);
}

#[test]
fn split_fixture_extracts_separate_av_and_subtitles() {
    let media = parse_player(&fixture("split")).unwrap();
    let normalized = crate::normalize_extracted_media(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        &media,
    )
    .unwrap();
    assert!(
        normalized
            .formats
            .iter()
            .any(|format| format.role == StreamRole::VideoOnly)
    );
    assert!(
        normalized
            .formats
            .iter()
            .any(|format| format.role == StreamRole::AudioOnly)
    );
    assert_eq!(normalized.subtitles.len(), 1);
    assert_eq!(normalized.subtitles[0].format.language, "en");
}

#[test]
fn unavailable_fixture_fails_closed() {
    let error = parse_player(&fixture("unavailable")).unwrap_err();
    assert_eq!(error.kind, ErrorKind::SourceChanged);
    assert!(!error.retryable);
}

#[test]
fn malformed_provider_url_is_rejected_during_normalization() {
    let extracted = parse_player(&fixture("malformed")).unwrap();
    let error =
        crate::normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &extracted).unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
}

#[test]
fn oversized_caption_url_is_rejected() {
    let huge = format!(
        "https://www.youtube.com/api/timedtext?x={}",
        "a".repeat(17_000)
    );
    let player = serde_json::json!({
        "playabilityStatus": {"status": "OK"},
        "videoDetails": {"title": "Bounded"},
        "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [{
            "baseUrl": huge,
            "languageCode": "en"
        }]}}
    });
    let error = parse_player(&player).unwrap_err();
    assert_eq!(error.kind, ErrorKind::SourceChanged);
}
