# Rust core foundation audit

This document qualifies OYP-101 through OYP-103 against the portable Rust core.

## OYP-101 — Domain models

`core/src/domain.rs` defines provider-independent `SourceIdentity`, `MediaInfo`, raw media/audio/video/subtitle models, `SubtitleTrack`, a curated `QualityChoice` distinct from raw `MediaFormat`, portable `LocalAsset` and `LibraryItem` records, download-plan types, and the typed `ErrorKind`/`CoreError` taxonomy. Local assets persist relative paths rather than Android URIs, and tests guard the provider-independent quality-choice and portable-path properties.

## OYP-102 — Download state machine

`core/src/state.rs` defines all required states: queued, resolving, downloading, paused, retry-wait, failed, verifying, completed, and canceled. `DownloadState::can_transition_to` is the single legal-transition contract and `DownloadStateMachine::transition` rejects every transition outside it without mutating state. The exhaustive matrix test iterates every pair of the nine states and checks implementation behavior against the contract; additional tests verify terminal states and retry constraints.

## OYP-103 — Core event model

`core/src/events.rs` separates durable queue reconstruction (`DurableDownloadSnapshot`) from ephemeral speed/ETA metrics (`EphemeralTransferMetrics`) and exposes coarse state/progress/library events through `CoreEvent`. `ProgressCoalescer` prevents per-network-chunk FFI/UI chatter by requiring both elapsed time and byte progress between ordinary emissions while still allowing first/final updates. Its tests cover coalescing and forced final emission.

## Qualification rule

OYP-101 through OYP-103 may be reconciled in `docs/OFFLINE_YT_PLAYER_TODO.md` after this audit commit passes exact-head CI. Exact qualifying CI evidence belongs in the reconciliation/closeout record.
