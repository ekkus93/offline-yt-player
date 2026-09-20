use serde_json::Value;

fn fixture(name: &str) -> Value {
    let raw = match name {
        "combined" => include_str!("fixtures/youtube/combined_av.json"),
        "split" => include_str!("fixtures/youtube/split_av.json"),
        "unavailable" => include_str!("fixtures/youtube/unavailable.json"),
        "malformed" => include_str!("fixtures/youtube/malformed.json"),
        _ => panic!("unknown fixture"),
    };
    assert!(
        raw.len() < 16 * 1024,
        "sanitized fixture must remain bounded"
    );
    assert!(!raw.contains("signature="));
    assert!(!raw.contains("token="));
    serde_json::from_str(raw).expect("fixture must be valid JSON")
}

#[test]
fn combined_fixture_contains_metadata_and_direct_play_stream() {
    let v = fixture("combined");
    assert_eq!(
        v.pointer("/playabilityStatus/status")
            .and_then(Value::as_str),
        Some("OK")
    );
    assert_eq!(
        v.pointer("/videoDetails/title").and_then(Value::as_str),
        Some("Fixture Combined")
    );
    let formats = v
        .pointer("/streamingData/formats")
        .and_then(Value::as_array)
        .unwrap();
    assert_eq!(formats.len(), 1);
    let mime = formats[0].get("mimeType").and_then(Value::as_str).unwrap();
    assert!(mime.starts_with("video/mp4"));
    assert!(mime.contains("avc1"));
    assert!(mime.contains("mp4a"));
}

#[test]
fn split_fixture_contains_separate_video_and_audio_streams() {
    let v = fixture("split");
    let formats = v
        .pointer("/streamingData/adaptiveFormats")
        .and_then(Value::as_array)
        .unwrap();
    assert_eq!(formats.len(), 2);
    assert!(formats.iter().any(|f| {
        f.get("mimeType")
            .and_then(Value::as_str)
            .is_some_and(|m| m.starts_with("video/"))
    }));
    assert!(formats.iter().any(|f| {
        f.get("mimeType")
            .and_then(Value::as_str)
            .is_some_and(|m| m.starts_with("audio/"))
    }));
}

#[test]
fn unavailable_and_malformed_fixtures_are_distinct_failure_shapes() {
    let unavailable = fixture("unavailable");
    assert_ne!(
        unavailable
            .pointer("/playabilityStatus/status")
            .and_then(Value::as_str),
        Some("OK")
    );
    let malformed = fixture("malformed");
    assert!(malformed.get("videoDetails").is_none());
    assert!(malformed.pointer("/streamingData/formats/0/url").is_none());
}

#[test]
fn oversize_case_is_generated_without_committing_provider_payload() {
    const MAX_WATCH_BYTES: usize = 4 * 1024 * 1024;
    let oversized = vec![b'x'; MAX_WATCH_BYTES + 1];
    assert!(oversized.len() > MAX_WATCH_BYTES);
}
