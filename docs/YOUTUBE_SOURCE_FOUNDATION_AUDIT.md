# OYP-701/OYP-702 YouTube Source Foundation Audit

## OYP-701 — Extraction strategy spike

`docs/YOUTUBE_EXTRACTION_STRATEGY.md` records the required engineering spike. It evaluates a narrow pure-Rust extractor against embedding/wrapping an external extractor, including Android ABI/runtime packaging, updateability, reliability, provenance, licensing, app-store/policy implications, and failure diagnostics. The documented v1 decision is a narrow pure-Rust implementation behind `MediaSource`; Python/yt-dlp/FFmpeg or runtime-downloaded executable payloads are explicitly not selected. OYP-706 remains an independent public-release policy/legal gate.

All four OYP-701 checklist requirements are therefore satisfied as an architecture decision; OYP-702 through OYP-705 remain responsible for qualifying the selected implementation.

## OYP-702 — URL recognition

`core/src/youtube.rs` implements `recognize_youtube_video_url` with strict host/path/video-ID validation. It accepts canonical `/watch?v=` URLs across the explicitly supported YouTube hosts and `youtu.be/<id>` share URLs, canonicalizes accepted identities to `https://www.youtube.com/watch?v=<id>`, and rejects playlists, channels, unsupported Shorts forms, extra share-path segments, spoofed hosts, invalid IDs, and unsupported schemes with typed `UnsupportedSource` errors.

The unit tests `recognizes_canonical_watch_url`, `recognizes_short_share_url`, `recognizes_mobile_watch_url`, `rejects_non_video_youtube_forms`, and `rejects_spoofed_hosts_and_invalid_ids` qualify the three OYP-702 checklist requirements deterministically without contacting YouTube.

## Result

OYP-701 and OYP-702 are implemented. Exact-head CI for this audit commit is the closeout evidence. The next substantive YouTube milestone is OYP-703 metadata resolution using checked-in sanitized fixtures and fail-closed parsing.
