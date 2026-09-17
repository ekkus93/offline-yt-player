# YouTube metadata, format, and diagnostics audit

This document records qualification evidence for OYP-703, OYP-704, and OYP-705 against the implementation currently behind the provider-neutral `MediaSource` boundary. It is an implementation audit, not a statement that public distribution or a particular use of YouTube content is permitted; OYP-706 remains the release-policy gate.

## OYP-703 — Metadata resolution

`core/src/youtube_extract.rs` defines the bounded extracted-media representation and converts it into provider-neutral `MediaInfo` through `normalize_extracted_media`.

The normalization path:

- derives the provider/source identity from strict YouTube URL recognition, preserving the canonical URL and 11-character video ID;
- carries title, optional duration, optional thumbnail, and available streams into the generic media model;
- sanitizes the remote title before it enters the domain model;
- validates thumbnail and stream URLs through the shared HTTP URL validator;
- rejects an extraction with no streams as `SourceChanged` rather than pretending it resolved successfully; and
- rejects malformed/non-HTTP media URLs rather than allowing them into a download plan.

The module's deterministic fixture tests exercise metadata normalization, canonical source identity, title sanitization, stream-role normalization, provider-independent quality choices, empty extraction rejection, and malformed media URL rejection. These tests are intentionally independent of live YouTube availability, so upstream/network churn cannot make CI nondeterministic.

Result: all OYP-703 requirements are represented and regression-qualified.

## OYP-704 — Format normalization

`normalize_stream` converts extractor-specific stream records into generic `MediaFormat` values. It explicitly classifies combined audio/video, video-only adaptive, and audio-only adaptive streams, and fails closed when a stream contains neither media kind.

The normalized model records the format ID, container, MIME type, bitrate, content length, video dimensions/FPS/codec, audio codec/channels, and direct-play compatibility. The compatibility policy recognizes the currently supported MP4/H.264/AAC, M4A/AAC, and WebM VP9/Opus combinations and otherwise requires a muxing/compatibility decision instead of silently claiming direct play.

`curate_quality_choices` collapses raw video formats into provider-independent quality choices, orders them by video height, deduplicates duplicate heights, carries estimated size, and distinguishes preferred combined direct-play assets from separate-asset and muxing-required choices.

Result: all OYP-704 requirements are represented and regression-qualified.

## OYP-705 — Adapter diagnostics

`core/src/youtube_diagnostics.rs` converts typed core failures into a deliberately sanitized `YouTubeDiagnostic` containing only a stable code, user-safe message, and retryability bit.

The mapping distinguishes network-unavailable/network-timeout/HTTP failures from `SourceChanged`, so extractor incompatibility is not mislabeled as a transient network problem. `SourceChanged` explicitly tells the user that YouTube delivery changed and that an app update may be required; unsupported URLs and unavailable compatible formats also receive distinct actionable categories.

The diagnostic boundary deliberately never reflects the original provider error message. This prevents cookies, bearer tokens, signed media URLs, signatures, expiry parameters, or other sensitive provider details embedded in an upstream error from crossing into logs/support diagnostics. Tests prove that signed-URL and authorization-like secret strings do not appear in the emitted diagnostic and that HTTP retryability is preserved without reflecting the original message.

Result: all OYP-705 requirements are represented and regression-qualified.

## Qualification boundary

This audit qualifies the normalization and diagnostic behavior that exists in the repository. It does not broaden the narrow pure-Rust extraction strategy chosen by OYP-701, does not authorize runtime executable updates or external extractor bundling, and does not close OYP-706. Public/app-store release remains blocked on the separate policy/legal review gate.
