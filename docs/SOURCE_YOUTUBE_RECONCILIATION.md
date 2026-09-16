# Source and YouTube TODO Reconciliation

This document reconciles the implementation evidence for OYP-601 through OYP-706 without overstating the remaining live-provider gap.

## OYP-601 through OYP-603

All OYP-601, OYP-602, and OYP-603 subtasks are implemented and qualified. `docs/SOURCE_ABSTRACTION_AUDIT.md` records the provider-neutral `MediaSource` contract, source registry behavior, optional subtitle/thumbnail hooks, and deterministic `DirectFixtureSource` E2E boundary. These items are eligible to be marked complete in `docs/OFFLINE_YT_PLAYER_TODO.md`.

## OYP-701

All four extraction-strategy spike subtasks are complete. `docs/YOUTUBE_EXTRACTION_STRATEGY.md` evaluates pure-Rust and external-extractor approaches, covers Android packaging/updateability/reliability/licensing/policy concerns, rejects runtime-downloaded executable extractors for v1, and chooses a narrow pure-Rust implementation behind `MediaSource`.

## OYP-702

All three URL-recognition subtasks are complete for the intentionally supported v1 forms. `core/src/youtube.rs` accepts canonical watch and `youtu.be` share URLs and rejects unsupported/non-video/spoofed forms with typed errors.

## OYP-703

This task is only partially complete. Remote metadata sanitization and deterministic fixture/regression qualification are implemented in `core/src/youtube_extract.rs`. The first subtask, live resolution of title/duration/thumbnail/source ID/available streams, remains open because the repository still lacks the chosen pure-Rust live YouTube provider extraction transport/signature implementation. The TODO must not mark OYP-703 fully complete until that adapter exists and is qualified.

## OYP-704

All three format-normalization subtasks are complete at the provider-normalization boundary. `core/src/youtube_extract.rs` normalizes combined/video-only/audio-only streams, retains codec/container/bitrate/resolution/compatibility metadata, and collapses raw video formats into curated quality choices. Device-specific compatibility remains separately gated by OYP-801 through OYP-803.

## OYP-705

All three diagnostics subtasks are complete. `core/src/youtube_diagnostics.rs` distinguishes network/provider-change/format failures, emits stable actionable categories, and has tests proving secret-like provider details are not reflected into diagnostics.

## OYP-706

All three engineering release-gate subtasks are complete. `docs/POLICY_LEGAL_RELEASE_GATE.md` documents service/app-store review requirements, defines the authorized-content supported-use statement, and explicitly blocks public/app-store distribution until a dated policy/legal review records an approved disposition. This does not itself constitute legal approval.

## Remaining milestone blocker

OYP-700 cannot be closed as a milestone until the live pure-Rust YouTube `MediaSource` extraction path required by OYP-703 is implemented and qualified. Public distribution additionally remains blocked by the policy/legal release gate even after technical adapter completion.
