# RMD-803 Metadata Presentation Audit — 2026-09-30

## Result

RMD-803 is **not yet complete** on current `master`. This audit records the concrete remaining production-path gap instead of checking the canonical TODO by assertion.

## Already implemented and qualified

Production source-analysis metadata is provider-neutral and bounded through `GeneratedUniffiSourceAnalysisGateway` and `SourceMetadataPolicy`. `SourceMetadataPolicy` bounds and sanitizes title, channel, description-like, quality-label, and diagnostic strings, including control-character removal and deterministic fallbacks. `SourceMetadataPolicyTest` covers malformed and very-long metadata as well as diagnostic URL redaction.

Production Library mapping in `MainActivity.toLibraryScreenState()` uses persisted `CoreLibraryItem.displayTitle`, duration, quality, and playback-asset availability. Display title and quality are passed through `SourceMetadataPolicy`; no preview fixture is substituted for persisted Library metadata.

Production Add/Analyze already consumes resolved source metadata through the generated UniFFI source-analysis gateway as qualified under RMD-702.

## Remaining blocker

Production Downloads mapping in `MainActivity.toDownloadsScreenState()` currently sets each row title from `snapshot.jobId`:

```kotlin
title = SourceMetadataPolicy.title(snapshot.jobId)
```

`CoreDownloadSnapshot` currently exposes durable queue identity/state/progress/error fields but does not expose a persisted display title or provider-neutral source metadata. Showing a job identifier as the user-facing title is not sufficient to claim "persisted/resolved production metadata in all screens" under RMD-803.

Therefore RMD-803 must remain unchecked until the durable queue/read model exposes an appropriate bounded display title (or an explicit join to persisted source/library metadata), Android maps that value into `DownloadRowModel`, and behavioral tests prove malformed/oversized metadata remains bounded in the Downloads presentation path.

## Required next implementation slice

1. Extend the durable queue/read boundary with provider-neutral display metadata needed by Downloads, without exposing provider payloads.
2. Carry that field through UniFFI and `CoreDownloadSnapshot`.
3. Replace the `jobId`-as-title production fallback with the persisted/resolved bounded title.
4. Add deterministic Rust/Android mapping tests for ordinary, malformed, and oversized titles.
5. Run the exact-head CI matrix before reconciling RMD-803 in the canonical TODO.

This audit is evidence for an open blocker, not a competing checklist and not completion evidence.
