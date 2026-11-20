use crate::{
    CoreError, DirectFixtureSource, ErrorKind, FfiCancellationToken, FfiError, FfiMediaSummary,
    FfiQualityChoice, FixtureMedia, MediaSource, YouTubeSource,
};
use futures::executor::block_on;
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiFixtureMedia {
    pub source_url: String,
    pub media_id: String,
    pub title: String,
    pub duration_ms: u64,
    pub media_url: String,
    pub thumbnail_url: Option<String>,
    pub bytes: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiResolveResult {
    pub media: Option<FfiMediaSummary>,
    pub error: Option<FfiError>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiChoiceListResult {
    pub choices: Vec<FfiQualityChoice>,
    pub error: Option<FfiError>,
}

/// Coarse source-operation boundary used by Android through `CoreCallDispatcher`.
///
/// The deterministic fixture constructor makes resolve/list-choice behavior executable in CI
/// without a live provider. Production adapters join this same source boundary as they are
/// qualified; provider-specific types never cross UniFFI.
#[derive(Debug, uniffi::Object)]
pub struct FfiSourceService {
    source: DirectFixtureSource,
}

#[uniffi::export]
impl FfiSourceService {
    #[uniffi::constructor]
    pub fn with_fixtures(fixtures: Vec<FfiFixtureMedia>) -> Arc<Self> {
        let entries = fixtures.into_iter().map(|fixture| {
            let url = fixture.source_url;
            (
                url,
                FixtureMedia {
                    media_id: fixture.media_id,
                    title: fixture.title,
                    duration_ms: fixture.duration_ms,
                    media_url: fixture.media_url,
                    thumbnail_url: fixture.thumbnail_url,
                    bytes: fixture.bytes,
                },
            )
        });
        Arc::new(Self {
            source: DirectFixtureSource::with_entries(entries),
        })
    }

    pub fn resolve(&self, url: String, cancel: Arc<FfiCancellationToken>) -> FfiResolveResult {
        match self.resolve_inner(&url, &cancel) {
            Ok(media) => FfiResolveResult {
                media: Some(FfiMediaSummary::from(&media)),
                error: None,
            },
            Err(error) => FfiResolveResult {
                media: None,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn list_choices(
        &self,
        url: String,
        cancel: Arc<FfiCancellationToken>,
    ) -> FfiChoiceListResult {
        match self.resolve_inner(&url, &cancel).and_then(|media| {
            cancel.check()?;
            block_on(self.source.choices(&media))
        }) {
            Ok(choices) => FfiChoiceListResult {
                choices: choices.iter().map(FfiQualityChoice::from).collect(),
                error: None,
            },
            Err(error) => FfiChoiceListResult {
                choices: Vec::new(),
                error: Some(FfiError::from(&error)),
            },
        }
    }
}

impl FfiSourceService {
    fn resolve_inner(
        &self,
        url: &str,
        cancel: &FfiCancellationToken,
    ) -> Result<crate::MediaInfo, CoreError> {
        cancel.check()?;
        if !self.source.can_handle(url) {
            return Err(CoreError::new(
                ErrorKind::UnsupportedSource,
                "This URL is not from a configured source",
                false,
            ));
        }
        let media = block_on(self.source.resolve(url))?;
        cancel.check()?;
        Ok(media)
    }
}

/// Production YouTube source boundary for Android.
///
/// This is deliberately separate from [`FfiSourceService`], whose fixture-only constructor remains
/// deterministic CI infrastructure. Production callers use this service so fixture configuration
/// can never accidentally stand in for live provider resolution.
#[derive(Debug, uniffi::Object)]
pub struct FfiYouTubeSourceService {
    source: YouTubeSource,
}

#[uniffi::export]
impl FfiYouTubeSourceService {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            source: YouTubeSource::default(),
        })
    }

    pub fn resolve(&self, url: String, cancel: Arc<FfiCancellationToken>) -> FfiResolveResult {
        match resolve_source(&self.source, &url, &cancel) {
            Ok(media) => FfiResolveResult {
                media: Some(FfiMediaSummary::from(&media)),
                error: None,
            },
            Err(error) => FfiResolveResult {
                media: None,
                error: Some(FfiError::from(&error)),
            },
        }
    }

    pub fn list_choices(
        &self,
        url: String,
        cancel: Arc<FfiCancellationToken>,
    ) -> FfiChoiceListResult {
        match resolve_source(&self.source, &url, &cancel).and_then(|media| {
            cancel.check()?;
            block_on(self.source.choices(&media))
        }) {
            Ok(choices) => FfiChoiceListResult {
                choices: choices.iter().map(FfiQualityChoice::from).collect(),
                error: None,
            },
            Err(error) => FfiChoiceListResult {
                choices: Vec::new(),
                error: Some(FfiError::from(&error)),
            },
        }
    }
}

fn resolve_source<S: MediaSource>(
    source: &S,
    url: &str,
    cancel: &FfiCancellationToken,
) -> Result<crate::MediaInfo, CoreError> {
    cancel.check()?;
    if !source.can_handle(url) {
        return Err(CoreError::new(
            ErrorKind::UnsupportedSource,
            "This URL is not from a configured source",
            false,
        ));
    }
    let media = block_on(source.resolve(url))?;
    cancel.check()?;
    Ok(media)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::FfiErrorKind;

    fn service() -> Arc<FfiSourceService> {
        FfiSourceService::with_fixtures(vec![FfiFixtureMedia {
            source_url: "https://fixture.invalid/watch/one".into(),
            media_id: "one".into(),
            title: "Fixture One".into(),
            duration_ms: 42_000,
            media_url: "https://fixture.invalid/media/one.mp4".into(),
            thumbnail_url: None,
            bytes: Some(1_024),
        }])
    }

    struct ControlledResolutionSource {
        inner: DirectFixtureSource,
        started: std::sync::mpsc::Sender<()>,
        release: std::sync::Mutex<std::sync::mpsc::Receiver<()>>,
        calls: std::sync::atomic::AtomicUsize,
    }

    impl MediaSource for ControlledResolutionSource {
        fn id(&self) -> &'static str {
            "controlled-fixture"
        }

        fn can_handle(&self, url: &str) -> bool {
            self.inner.can_handle(url)
        }

        fn resolve<'a>(&'a self, url: &'a str) -> crate::SourceFuture<'a, crate::MediaInfo> {
            Box::pin(async move {
                self.calls.fetch_add(1, std::sync::atomic::Ordering::AcqRel);
                self.started.send(()).expect("notify test source entry");
                // Model a blocking source request on a dedicated worker thread;
                // cancellation is observed after the operation unblocks.
                {
                    let release = self.release.lock().expect("source release lock");
                    release
                        .recv_timeout(std::time::Duration::from_secs(10))
                        .expect("bounded fixture operation must be released");
                }
                self.inner.resolve(url).await
            })
        }

        fn choices<'a>(
            &'a self,
            media: &'a crate::MediaInfo,
        ) -> crate::SourceFuture<'a, Vec<crate::QualityChoice>> {
            self.inner.choices(media)
        }

        fn download_plan<'a>(
            &'a self,
            media: &'a crate::MediaInfo,
            choice_id: &'a str,
        ) -> crate::SourceFuture<'a, crate::DownloadPlan> {
            self.inner.download_plan(media, choice_id)
        }
    }

    #[test]
    fn canceled_source_boundary_before_dispatch_never_starts_work() {
        let (started, observed) = std::sync::mpsc::channel();
        let (_release, receiver) = std::sync::mpsc::channel();
        let source = ControlledResolutionSource {
            inner: DirectFixtureSource::with_entries([(
                "https://fixture.invalid/video".into(),
                FixtureMedia {
                    media_id: "video".into(),
                    title: "Fixture".into(),
                    duration_ms: 1_000,
                    media_url: "https://fixture.invalid/media".into(),
                    thumbnail_url: None,
                    bytes: None,
                },
            )]),
            started,
            release: std::sync::Mutex::new(receiver),
            calls: std::sync::atomic::AtomicUsize::new(0),
        };
        let cancel = FfiCancellationToken::new();
        cancel.cancel();
        let failure = resolve_source(&source, "https://fixture.invalid/video", &cancel)
            .expect_err("pre-cancellation must fail closed");
        assert_eq!(failure.kind, ErrorKind::Canceled);
        assert_eq!(source.calls.load(std::sync::atomic::Ordering::Acquire), 0);
        assert!(observed.try_recv().is_err());
    }

    #[test]
    fn in_flight_cancellation_disallows_completion_after_blocked_source_returns() {
        let (started, entered) = std::sync::mpsc::channel();
        let (release, unblock) = std::sync::mpsc::channel();
        let source = Arc::new(ControlledResolutionSource {
            inner: DirectFixtureSource::with_entries([(
                "https://fixture.invalid/video".into(),
                FixtureMedia {
                    media_id: "video".into(),
                    title: "Fixture".into(),
                    duration_ms: 1_000,
                    media_url: "https://fixture.invalid/media".into(),
                    thumbnail_url: None,
                    bytes: None,
                },
            )]),
            started,
            release: std::sync::Mutex::new(unblock),
            calls: std::sync::atomic::AtomicUsize::new(0),
        });
        let token = FfiCancellationToken::new();
        let worker_token = Arc::clone(&token);
        let worker_source = Arc::clone(&source);
        let worker = std::thread::spawn(move || {
            resolve_source(
                worker_source.as_ref(),
                "https://fixture.invalid/video",
                &worker_token,
            )
        });
        entered
            .recv_timeout(std::time::Duration::from_secs(10))
            .expect("source must enter controlled operation");
        token.cancel();
        assert!(token.is_canceled());
        release.send(()).expect("release blocked source");
        let result = worker.join().expect("worker must terminate");
        let error = result.expect_err("canceled operation must never complete successfully");
        assert_eq!(error.kind, ErrorKind::Canceled);
        assert!(!error.retryable);
        assert_eq!(source.calls.load(std::sync::atomic::Ordering::Acquire), 1);
    }

    #[test]
    fn resolves_and_lists_choices_through_coarse_ffi_records() {
        let service = service();
        let token = FfiCancellationToken::new();
        let resolved = service.resolve(
            "https://fixture.invalid/watch/one".into(),
            Arc::clone(&token),
        );
        assert!(resolved.error.is_none());
        let media = resolved.media.unwrap();
        assert_eq!(media.source.provider, "direct-fixture");
        assert_eq!(media.source.media_id, "one");
        assert_eq!(media.title, "Fixture One");

        let listed = service.list_choices(
            "https://fixture.invalid/watch/one".into(),
            Arc::clone(&token),
        );
        assert!(listed.error.is_none());
        assert_eq!(listed.choices.len(), 1);
        assert_eq!(listed.choices[0].label, "720p");
    }


    #[test]
    fn malformed_source_requests_fail_with_typed_error_not_empty_success() {
        let service = service();
        for input in [
            "",
            "javascript:alert(1)",
            "https://fixture.invalid/watch/one?sig=PRIVATE_QUERY_TOKEN",
            "https://other.invalid/watch/one",
        ] {
            let result = service.resolve(input.into(), FfiCancellationToken::new());
            assert!(result.media.is_none(), "malformed request must have no media");
            let error = result.error.expect("malformed input must report an error");
            assert_eq!(error.kind, FfiErrorKind::UnsupportedSource);
            assert!(!error.message.contains("PRIVATE_QUERY_TOKEN"));

            let choices = service.list_choices(input.into(), FfiCancellationToken::new());
            assert!(choices.choices.is_empty());
            assert_eq!(
                choices.error.expect("missing choices must not be treated as success").kind,
                FfiErrorKind::UnsupportedSource
            );
        }
    }

    #[test]
    fn production_youtube_service_rejects_non_youtube_without_network_access() {
        let service = FfiYouTubeSourceService::new();
        let result = service.resolve(
            "https://example.invalid/not-youtube".into(),
            FfiCancellationToken::new(),
        );
        assert_eq!(result.error.unwrap().kind, FfiErrorKind::UnsupportedSource);
    }

    #[test]
    fn production_youtube_service_honors_pre_cancellation_without_network_access() {
        let service = FfiYouTubeSourceService::new();
        let token = FfiCancellationToken::new();
        token.cancel();
        let result = service.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ".into(), token);
        assert_eq!(result.error.unwrap().kind, FfiErrorKind::Canceled);
    }

    #[test]
    fn source_operations_return_typed_unsupported_and_canceled_errors() {
        let service = service();
        let unsupported = service.resolve(
            "https://fixture.invalid/watch/missing".into(),
            FfiCancellationToken::new(),
        );
        assert_eq!(
            unsupported.error.unwrap().kind,
            FfiErrorKind::UnsupportedSource
        );

        let token = FfiCancellationToken::new();
        token.cancel();
        let canceled = service.list_choices(
            "https://fixture.invalid/watch/one".into(),
            Arc::clone(&token),
        );
        assert_eq!(canceled.error.unwrap().kind, FfiErrorKind::Canceled);
    }
}
