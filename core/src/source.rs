use crate::domain::{
    CoreError, DownloadPlan, DownloadPlanAsset, ErrorKind, MediaFormat, MediaInfo, MediaKind,
    QualityChoice, SourceIdentity, SubtitleTrack,
};
use crate::security::{sanitize_filename, sanitize_title, validate_http_url};
use std::collections::HashMap;
use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;

pub type SourceFuture<'a, T> = Pin<Box<dyn Future<Output = Result<T, CoreError>> + Send + 'a>>;

/// Provider abstraction. Provider-specific behavior remains behind this contract.
pub trait MediaSource: Send + Sync {
    fn id(&self) -> &'static str;
    fn can_handle(&self, url: &str) -> bool;
    fn resolve<'a>(&'a self, url: &'a str) -> SourceFuture<'a, MediaInfo>;
    fn formats<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Vec<MediaFormat>> {
        Box::pin(async move { Ok(media.formats.clone()) })
    }
    fn subtitles<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Vec<SubtitleTrack>> {
        Box::pin(async move { Ok(media.subtitles.clone()) })
    }
    fn thumbnail_url<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Option<String>> {
        Box::pin(async move { Ok(media.thumbnail_url.clone()) })
    }
    fn choices<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Vec<QualityChoice>>;
    fn download_plan<'a>(
        &'a self,
        media: &'a MediaInfo,
        choice_id: &'a str,
    ) -> SourceFuture<'a, DownloadPlan>;
}

#[derive(Default)]
pub struct SourceRegistry {
    sources: Vec<Arc<dyn MediaSource>>,
}

impl SourceRegistry {
    #[must_use]
    pub fn new() -> Self { Self::default() }

    /// Production registry. Fixture adapters are deliberately not installed here.
    #[must_use]
    pub fn production() -> Self {
        let mut registry = Self::new();
        registry.register(Arc::new(crate::YouTubeSource::default()));
        registry
    }

    pub fn register(&mut self, source: Arc<dyn MediaSource>) { self.sources.push(source); }

    pub fn select(&self, url: &str) -> Result<Arc<dyn MediaSource>, CoreError> {
        validate_http_url(url)?;
        self.sources.iter().find(|source| source.can_handle(url)).cloned().ok_or_else(|| {
            CoreError::new(ErrorKind::UnsupportedSource, "This URL is not from a supported source", false)
        })
    }
}

/// Deterministic non-YouTube adapter used by CI and end-to-end tests.
#[derive(Debug, Clone, Default)]
pub struct DirectFixtureSource { entries: Arc<HashMap<String, FixtureMedia>> }

#[derive(Debug, Clone)]
pub struct FixtureMedia {
    pub media_id: String, pub title: String, pub duration_ms: u64, pub media_url: String,
    pub thumbnail_url: Option<String>, pub bytes: Option<u64>,
}

impl DirectFixtureSource {
    #[must_use]
    pub fn with_entries(entries: impl IntoIterator<Item = (String, FixtureMedia)>) -> Self {
        Self { entries: Arc::new(entries.into_iter().collect()) }
    }
    fn fixture(&self, url: &str) -> Result<&FixtureMedia, CoreError> {
        self.entries.get(url).ok_or_else(|| CoreError::new(ErrorKind::UnsupportedSource, "Fixture URL is not registered", false))
    }
}

impl MediaSource for DirectFixtureSource {
    fn id(&self) -> &'static str { "direct-fixture" }
    fn can_handle(&self, url: &str) -> bool { self.entries.contains_key(url) }
    fn resolve<'a>(&'a self, url: &'a str) -> SourceFuture<'a, MediaInfo> {
        Box::pin(async move {
            let fixture = self.fixture(url)?;
            Ok(MediaInfo { source: SourceIdentity { provider: self.id().into(), media_id: fixture.media_id.clone(), canonical_url: Some(url.into()) }, title: sanitize_title(&fixture.title), duration_ms: Some(fixture.duration_ms), thumbnail_url: fixture.thumbnail_url.clone(), formats: vec![MediaFormat { format_id: "fixture-combined".into(), container: "mp4".into(), mime_type: Some("video/mp4".into()), role: crate::domain::StreamRole::Combined, bitrate_bps: None, content_length: fixture.bytes, video: Some(crate::domain::VideoFormat { width: 1280, height: 720, fps: Some(30), codec: "h264".into() }), audio: Some(crate::domain::AudioFormat { codec: "aac".into(), bitrate_bps: None, channels: Some(2), language: None }), compatible_direct_play: true }], subtitles: Vec::new() })
        })
    }
    fn choices<'a>(&'a self, media: &'a MediaInfo) -> SourceFuture<'a, Vec<QualityChoice>> {
        Box::pin(async move { let format = media.formats.first().ok_or_else(|| CoreError::new(ErrorKind::NoCompatibleFormat, "No fixture format", false))?; Ok(vec![QualityChoice { choice_id: "fixture-720p".into(), label: "720p".into(), estimated_bytes: format.content_length, video_height: Some(720), audio_only: false, compatibility: crate::domain::Compatibility::Preferred }]) })
    }
    fn download_plan<'a>(&'a self, media: &'a MediaInfo, choice_id: &'a str) -> SourceFuture<'a, DownloadPlan> {
        Box::pin(async move {
            if choice_id != "fixture-720p" { return Err(CoreError::new(ErrorKind::NoCompatibleFormat, "Unknown fixture quality", false)); }
            let canonical = media.source.canonical_url.as_deref().ok_or_else(|| CoreError::new(ErrorKind::InvalidInput, "Missing canonical URL", false))?;
            let fixture = self.fixture(canonical)?; validate_http_url(&fixture.media_url)?;
            let file = format!("{}.mp4", sanitize_filename(&fixture.media_id));
            Ok(DownloadPlan { source: media.source.clone(), title: media.title.clone(), quality: QualityChoice { choice_id: choice_id.into(), label: "720p".into(), estimated_bytes: fixture.bytes, video_height: Some(720), audio_only: false, compatibility: crate::domain::Compatibility::Preferred }, assets: vec![DownloadPlanAsset { asset_id: "combined".into(), kind: MediaKind::Video, url: fixture.media_url.clone(), relative_path: format!("items/{}/{file}", sanitize_filename(&fixture.media_id)), expected_bytes: fixture.bytes, expected_sha256: None, mime_type: Some("video/mp4".into()) }] })
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn registry_rejects_unknown_sources() { let registry=SourceRegistry::new(); assert_eq!(registry.select("https://example.com/video").err().unwrap().kind, ErrorKind::UnsupportedSource); }
    #[test] fn production_registry_selects_youtube() { let source=SourceRegistry::production().select("https://youtu.be/dQw4w9WgXcQ").unwrap(); assert_eq!(source.id(), "youtube"); }
    #[test] fn fixture_adapter_is_provider_independent() { let source=DirectFixtureSource::with_entries([("https://fixture.invalid/watch/1".into(), FixtureMedia { media_id:"one".into(), title:"Example".into(), duration_ms:10_000, media_url:"https://fixture.invalid/media/one.mp4".into(), thumbnail_url:None, bytes:Some(1_024) })]); assert!(source.can_handle("https://fixture.invalid/watch/1")); assert!(!source.can_handle("https://youtube.com/watch?v=one")); }
}
