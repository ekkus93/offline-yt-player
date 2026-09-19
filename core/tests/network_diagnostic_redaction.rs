use offline_yt_core::{DownloadEngine, DownloadPolicy, TransferRequest};
use std::sync::atomic::AtomicBool;

const SECRET_MARKERS: [&str; 4] = [
    "SIGNED_QUERY_SECRET_RMD406",
    "TOKEN_SECRET_RMD406",
    "COOKIE_SECRET_RMD406",
    "BEARER_SECRET_RMD406",
];

#[test]
fn download_connect_failure_does_not_reflect_signed_url_or_secret_markers() {
    let temp = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        connect_timeout: std::time::Duration::from_millis(100),
        request_timeout: std::time::Duration::from_millis(200),
        ..DownloadPolicy::default()
    };
    let engine = DownloadEngine::new(temp.path(), policy).unwrap();
    let request = TransferRequest {
        url: format!(
            "http://127.0.0.1:1/media?sig={}&token={}&cookie={}&authorization=Bearer%20{}",
            SECRET_MARKERS[0], SECRET_MARKERS[1], SECRET_MARKERS[2], SECRET_MARKERS[3]
        ),
        relative_path: "items/redaction/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    };

    let error = engine
        .transfer(&request, &AtomicBool::new(false))
        .expect_err("closed local port must fail without exposing the request URL");

    let rendered = format!("{error:?} {}", error.message);
    assert!(!rendered.contains("http://"));
    assert!(!rendered.contains("?sig="));
    for marker in SECRET_MARKERS {
        assert!(
            !rendered.contains(marker),
            "network diagnostic reflected injected secret marker {marker}"
        );
    }
}
