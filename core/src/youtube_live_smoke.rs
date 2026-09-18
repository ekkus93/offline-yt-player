use crate::{MediaSource, YouTubeSource};

/// Opt-in network qualification. Normal CI never supplies the URL and never runs ignored tests.
#[test]
#[ignore = "manual live YouTube qualification; requires explicit policy approval and network access"]
fn live_youtube_source_resolves_supported_url() {
    let url = std::env::var("OYP_YOUTUBE_LIVE_URL")
        .expect("set OYP_YOUTUBE_LIVE_URL to an approved public YouTube video URL");
    let source = YouTubeSource::default();
    assert!(source.can_handle(&url));
    let media = futures::executor::block_on(source.resolve(&url)).expect(
        "live adapter failed; SourceChanged indicates provider structure may require remediation",
    );
    assert_eq!(media.source.provider, "youtube");
    assert!(!media.source.media_id.is_empty());
    assert!(!media.title.is_empty());
    assert!(!media.formats.is_empty());
}
