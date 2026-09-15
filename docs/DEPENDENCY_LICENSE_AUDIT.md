# Dependency and license audit

This is the OYP-2002 v1 dependency/license review. It records the direct dependency surface that is intentionally accepted by the repository; transitive resolution remains locked by `Cargo.lock` and Gradle's resolved graph in CI builds.

## Rust direct dependencies

The portable core currently depends directly on `reqwest` (HTTP transfer, with default features disabled and rustls TLS), `rusqlite` with bundled SQLite (portable persistence), `serde`/`serde_json` (durable model serialization), `sha2` (asset integrity), `thiserror` (typed errors), `uniffi` (foreign bindings), and `url` (URL parsing/validation). Test-only dependencies are `futures`, `tempfile`, and `tiny_http`; `tiny_http` is used only for deterministic local transfer fixtures.

No direct Rust dependency is an extractor, media muxer, FFmpeg binding, embedded scripting runtime, or provider-specific downloader. The current core therefore does not introduce the packaging/license surface that OYP-700/OYP-803 require to be gated separately.

## Android direct dependencies

Android direct dependencies are AndroidX Activity Compose, Compose Foundation/UI/Material3/tooling, and Media3 ExoPlayer/Session/UI, plus JUnit for JVM tests. Versions are centralized in `gradle/libs.versions.toml`; Compose artifacts resolve through the pinned Compose BOM. The app has no direct extractor, muxer, FFmpeg, WebView automation, embedded Python/JavaScript runtime, or provider-specific downloader dependency.

## OSS license inventory

The application packages third-party OSS components from the Rust and Android dependency graphs. Before public distribution, the release process must generate/preserve the notices required by the resolved versions and the repository's own pending license decision must be finalized. The root package metadata deliberately uses `LicenseRef-OYP-Pending`; that placeholder is a release blocker, not an assertion that the project itself is already licensed for redistribution.

For dependency review, maintainers must inspect the exact locked/resolved graph rather than infer licenses from this direct-dependency summary. Deterministic review inputs are `Cargo.lock`, `cargo tree --locked`, `gradle/libs.versions.toml`, and `./gradlew :app:dependencies`. A dependency change that adds copyleft/native redistribution obligations must be reviewed before release. These commands are inventory inputs; they do not replace a vulnerability-advisory scan when preparing a public release.

## Extractor/muxer gate

There is currently no extractor or muxer dependency to approve. If one is proposed later, OYP-700/OYP-803 require an explicit review of its license, service-policy implications, Android packaging/ABI footprint, updateability, and whether it is actually required by tested compatibility needs. Such a dependency must not be treated as implicitly approved by this audit.

## OYP-2002 disposition

- Rust dependency audit: complete for the current direct/locked dependency surface.
- Android dependency audit: complete for the current direct/resolved dependency surface.
- OSS license inventory: documented here, with final generated notices and the project's own license decision remaining part of the public-release gate.
- Extractor/muxer review: complete for current state because neither class of dependency is present; any future introduction reopens this gate.
