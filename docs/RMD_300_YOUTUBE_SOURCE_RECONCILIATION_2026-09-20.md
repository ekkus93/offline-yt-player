# RMD-300 production YouTube source reconciliation

This evidence note reconciles the current production YouTube adapter against RMD-301 through RMD-304 without replacing or compressing the detailed remediation TODO. The TODO remains authoritative, and a checkbox may only be changed when its individual requirement is supported by implementation and behavioral evidence.

## RMD-301 — live production `MediaSource`

Implemented in production code. `core/src/source.rs` defines `MediaSource` and `SourceRegistry::production()`, which registers `YouTubeSource` and deliberately excludes `DirectFixtureSource`. `core/src/youtube.rs` recognizes the supported watch/share URL forms, validates the 11-character video ID, and canonicalizes identity. `core/src/youtube_source.rs` resolves title, duration, thumbnail, stream formats and subtitles from bounded YouTube player data and produces provider-neutral `DownloadPlan` assets. The HTTP client applies connection/overall timeouts and a bounded redirect policy; watch responses, stream count, subtitle count, media URLs, caption URLs, title and language metadata are bounded.

## RMD-302 — provider isolation and diagnostics

Implemented. Provider parsing remains inside `youtube_source.rs`/`youtube_extract.rs`; Android consumes provider-neutral UniFFI/core models through `AppSourceAnalysisGateway`. Unsupported URLs are rejected as `UnsupportedSource`; non-success HTTP responses are `HttpStatus`; transport failures are mapped to a safe `NetworkUnavailable` diagnostic; malformed/missing provider structures and unavailable media fail closed as `SourceChanged`. Raw provider payloads are not exposed through Android UI models.

## RMD-303 — deterministic adapter qualification

Partially implemented and therefore remains fail-closed as a milestone. Existing unit tests cover URL recognition, executable provider-neutral assets, caption extraction, malformed caption URLs, extraction normalization/curation, and production-vs-fixture registry separation. The detailed requirement calls for bounded sanitized representative provider-response fixtures covering combined A/V, split A/V, unavailable/private/changed-source, and malformed/oversized responses. Until those fixture cases are all present and qualified, RMD-303 must remain unchecked.

## RMD-304 — controlled live-source qualification

Implemented as an opt-in manual smoke in `core/src/youtube_source.rs`: `live_youtube_resolves_real_metadata_and_formats` is ignored by normal CI and requires only `OYP_LIVE_YOUTUBE_VIDEO_ID`, a bare validated 11-character video ID. It resolves through the real production adapter and asserts production provider identity, metadata, formats and curated choices. The test text explicitly treats it as manual live YouTube qualification rather than deterministic CI proof. External YouTube/service-policy/legal approval remains a separate human release gate and is not approved by this engineering evidence.

## Reconciliation disposition

RMD-301, RMD-302 and RMD-304 have production implementation evidence on current master `0f5efc619ec8c700dedeb897ab9bb659466804a3`; post-merge CI run `35507540263` passed on that exact SHA. RMD-303 remains open because the complete deterministic sanitized provider-response fixture matrix required by the detailed TODO has not yet been demonstrated. Consequently the overall RMD-300 acceptance remains fail-closed until RMD-303 is completed and the production registry/live-source qualification evidence is incorporated into the detailed TODO.
