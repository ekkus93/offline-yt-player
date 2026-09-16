# YouTube Adapter Foundation Audit

This audit maps the current portable YouTube foundation to OYP-702 through OYP-705 without claiming that live extraction or the OYP-701 strategy/release gates are complete.

## OYP-702 — URL recognition

`core/src/youtube.rs` implements strict URL recognition using `url::Url`. It accepts canonical `/watch?v=` URLs on the supported YouTube hosts and `youtu.be/<id>` share URLs, canonicalizes accepted input to `https://www.youtube.com/watch?v=<id>`, validates the 11-character video ID alphabet, and rejects playlists, channels, Shorts, extra path segments, spoofed hosts, malformed IDs, and non-HTTP(S) schemes.

Deterministic unit tests cover canonical, short/share, mobile, unsupported-form, spoofed-host, and invalid-ID cases. OYP-702 is implementation-complete.

## OYP-703 — Metadata resolution boundary

`core/src/youtube_extract.rs` defines provider-extraction DTOs and `normalize_extracted_media`, which converts extractor output into portable `MediaInfo`. It records title, duration, thumbnail, source identity and streams; canonicalizes source identity through the URL recognizer; sanitizes the remote title; validates thumbnail and stream URLs; and rejects empty/malformed extraction with typed errors.

Fixture tests cover normalized metadata, stream roles, title sanitization, and malformed extraction. This qualifies the normalization boundary, but an actual live extraction backend is still required by OYP-701 before the end-to-end meaning of “resolve” is complete.

## OYP-704 — Format normalization

`normalize_stream` maps extractor records into provider-independent `MediaFormat`, including container, MIME type, bitrate, content length, dimensions, FPS, video/audio codecs, channel count, stream role, and direct-play compatibility. `curate_quality_choices` collapses raw video formats into provider-independent choices and labels them as preferred, separate-assets, or muxing-required.

Deterministic tests prove combined/video-only role normalization and curated compatibility classification. The portable normalization/curation portion of OYP-704 is complete.

## OYP-705 — Adapter diagnostics

Current code distinguishes malformed/unsupported URLs (`UnsupportedSource`), invalid remote URLs (`InvalidInput`), and extractor-shape/source breakage (`SourceChanged`). The portable structures do not contain cookies or request headers and no diagnostic logging of signed stream URLs is present in these modules.

However, network failure versus extractor incompatibility cannot be fully qualified until the chosen extraction backend exists. User-facing error mapping also depends on the still-open FFI/UI surface. OYP-705 therefore remains partially open.

## Remaining gates

- OYP-701 still needs a documented extraction-strategy decision and an implementation behind `MediaSource`.
- OYP-703 needs that backend to perform real resolution rather than only normalize extractor output.
- OYP-705 needs backend-specific network/extractor diagnostics and user-facing mapping qualification.
- OYP-706 policy/legal release review remains independent and must block public distribution until explicitly resolved.

This audit is evidence for completed URL recognition and portable normalization only; it must not be used to imply that live YouTube extraction or public-release policy approval exists.