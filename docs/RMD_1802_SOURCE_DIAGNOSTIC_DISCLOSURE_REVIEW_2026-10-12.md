# RMD-1802 — Source-analysis diagnostic disclosure review (2026-10-12)

## Production finding

`app/src/main/java/com/ekkus/offlineytplayer/coregateway/SourceAnalysisUseCase.kt` classified errors by `CoreGatewayError.kind`, but previously passed the raw `error.message` through to `SourceAnalysisState.Unsupported`, `NetworkFailure`, `SourceChanged`, or `Failed`. `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt::sourceAnalysisStatusMessage` then placed those messages directly into the Add/Analyze Compose status text. Provider or FFI diagnostics can contain signed URLs, authorization headers, cookies, local paths, or other private transport details. This is a production-path disclosure risk, not a policy-only test issue.

## Direct-to-master mitigation

- Commit `00e57e954fc983514c154ef1cea397020fd40476` changes `SourceAnalysisUseCase.classify` to publish fixed human-readable, category-specific diagnostics. The original `kind` and `retryable` attributes remain available internally for structured behavior. The UI no longer renders arbitrary provider error messages through this route.
- Commit `3b917e81472a19fc0edb943d91e7193ac9ecce6d` preserves the previously established fixed `Source analysis is unavailable.` presentation for `SOURCE_ANALYSIS_UNAVAILABLE` without trusting any upstream message payload.
- `app/src/test/java/com/ekkus/offlineytplayer/coregateway/SourceAnalysisUseCaseTest.kt::providerErrorsNeverExposeSecretsInPresentationState` injects an Authorization header, cookie and signed URL into errors of types unsupported, network, source-changed, parser and general failure and asserts none is exposed. Existing `thrownSourceGatewayDoesNotCrashAndDoesNotRevealPrivateDiagnostics` verifies the safe gateway-exception behavior.

## Qualification and limitations

The initial commit `00e57e9` reached JVM tests; CI `38347400343` failed one of 397 tests because the prior safe gateway-unavailable wording changed. This is addressed in `3b917e8`, which requires its own exact-head full CI results. No test result from the earlier SHA is evidence that `3b917e8` passed. Standard exact-head qualification includes Rust, Android JVM/packaging/ABI, Android smoke/Compose/goldens, FGS timeout, cold start, deterministic fixture, Android real-network E2E, supply chain and CI-evidence workflows.

The separate Android smoke run `38344066848` on older `05e809e0df462c1e5aa0bdcd728e177a6c3881c5` failed attempt 1 during Gradle dependency resolution and attempt 2 on `Rmd1802DownloadsControlThreadingInstrumentedTest` with `CalledFromWrongThreadException`; attempt 3 was requested. Those failures must not be misattributed to the source-error mitigation and any unresolved smoke failure must be diagnosed and qualified on the final head.

**Status:** production fix and host-side regression committed; final exact-head qualification, completion of independent production review, canonical RMD-1802/1803/1805 checkboxes and external YouTube/service-policy/legal approval remain **open**.
