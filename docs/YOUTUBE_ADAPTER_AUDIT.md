# YouTube Adapter Qualification Audit

This audit records the current qualification state of OYP-701 through OYP-705. It deliberately separates implemented deterministic provider-boundary work from the still-missing live extraction adapter. Public distribution remains gated by OYP-706.

## OYP-701 — extraction strategy spike

`docs/YOUTUBE_EXTRACTION_STRATEGY.md` evaluates a narrow pure-Rust extractor against embedded/wrapped external extractors. It records Android packaging, updateability, reliability, supply-chain, licensing, and policy implications, rejects runtime-downloaded executable extractors for v1, and chooses a narrow pure-Rust implementation behind `MediaSource`. The strategy explicitly requires checked-in fixtures and fail-closed `SourceChanged` behavior.

Status: engineering spike complete. The live pure-Rust extraction transport/player-signature implementation remains subsequent adapter work and is not implied by this audit.

## OYP-702 — URL recognition

`core/src/youtube.rs` accepts canonical `youtube.com/watch?v=...` forms (including mobile/music hosts) and `youtu.be/<id>` share links, canonicalizes them, validates the 11-character video identifier, and rejects playlists, channel pages, unsupported paths, spoofed hosts, malformed IDs, and non-HTTP(S) schemes. Unit tests cover accepted and rejected forms.

Status: complete for the intentionally supported v1 URL forms.

## OYP-703 — metadata normalization

`core/src/youtube_extract.rs` defines provider-side extracted metadata/stream records and converts them into portable `MediaInfo`. It derives the source identity from the recognized URL, sanitizes remote titles, validates thumbnail and media URLs, normalizes stream roles, and rejects empty/malformed extraction as typed failures. Deterministic unit fixtures cover metadata sanitization and malformed extraction.

Status: normalization and fixture regression boundary complete; acquisition of live provider responses remains open.

## OYP-704 — format normalization

`normalize_stream` maps extracted combined, video-only, and audio-only streams into provider-neutral `MediaFormat`, retaining container, MIME type, bitrate, content length, dimensions, frame rate, codecs, and audio channel information. `curate_quality_choices` collapses video formats into quality choices and labels direct-play, separate-asset, and muxing compatibility.

Status: deterministic normalization/curation complete. Broader codec/device compatibility remains governed by OYP-801 through OYP-803.

## OYP-705 — sanitized diagnostics

`core/src/youtube_diagnostics.rs` maps typed provider errors to stable diagnostic codes, actionable user-facing messages, and retryability without reflecting the original provider error message. This distinguishes network unavailable/timeout/HTTP failures from `SourceChanged` extractor incompatibility and `NoCompatibleFormat`. Tests inject signed-URL/token/cookie-like secrets and prove that diagnostic output does not reproduce them.

Status: complete for the typed diagnostic boundary.

## Remaining adapter gap

The repository does not yet contain a live YouTube `MediaSource` implementation that performs the chosen pure-Rust provider extraction and produces these normalized records. Therefore this audit must not be interpreted as completing the entire OYP-700 milestone. The next substantive implementation slice is the provider extraction/adapter path, followed by OYP-706 policy/legal release reconciliation.

## Qualification

All implemented boundaries above are covered by the normal Rust CI matrix. Exact-head CI for the commit containing this audit is required before merge. TODO checkbox reconciliation should only mark the specific completed subtasks and must leave the live-extraction gap explicit.
