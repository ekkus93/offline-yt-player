use crate::{ErrorKind, ExtractedYouTubeMedia, StreamRole, normalize_extracted_media};

fn fixture(name: &str) -> ExtractedYouTubeMedia {
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
    let media = normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &fixture("combined"))
        .unwrap();
    assert_eq!(media.title, "Fixture Combined");
    assert_eq!(media.duration_ms, Some(42_000));
    assert_eq!(media.thumbnail_url.as_deref(), Some("https://i.example/combined.jpg"));
    assert_eq!(media.formats.len(), 1);
    assert_eq!(media.formats[0].role, StreamRole::Combined);
}

#[test]
fn split_fixture_extracts_separate_av_and_subtitles() {
    let media = normalize_extracted_media(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        &fixture("split"),
    )
    .unwrap();
    assert!(media.formats.iter().any(|format| format.role == StreamRole::VideoOnly));
    assert!(media.formats.iter().any(|format| format.role == StreamRole::AudioOnly));
    assert_eq!(media.subtitles.len(), 1);
    assert_eq!(media.subtitles[0].format.language, "en");
}

#[test]
fn unavailable_fixture_fails_closed() {
    let error = normalize_extracted_media(
        "https://youtu.be/dQw4w9WgXcQ",
        &fixture("unavailable"),
    )
    .unwrap_err();
    assert_eq!(error.kind, ErrorKind::SourceChanged);
    assert!(!error.retryable);
}

#[test]
fn malformed_provider_url_is_rejected() {
    let error = normalize_extracted_media(
        "https://youtu.be/dQw4w9WgXcQ",
        &fixture("malformed"),
    )
    .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
}

#[test]
fn subtitle_normalization_is_bounded() {
    let mut extracted = fixture("combined");
    extracted.subtitles = (0..200)
        .map(|index| crate::ExtractedSubtitle {
            id: format!("track-{index}"),
            language: "en".into(),
            label: None,
            auto_generated: false,
        })
        .collect();
    let media = normalize_extracted_media("https://youtu.be/dQw4w9WgXcQ", &extracted).unwrap();
    assert_eq!(media.subtitles.len(), 128);
}
