# Source Abstraction Qualification Audit

This audit qualifies OYP-601 through OYP-603 against the portable Rust core.

## OYP-601 — `MediaSource` contract

`core/src/source.rs` defines the provider-neutral `MediaSource` trait with `can_handle`, asynchronous metadata `resolve`, normalized `formats`, curated `choices`, `download_plan`, and optional/default subtitle and thumbnail metadata hooks. The trait returns portable domain types only and keeps provider implementation details behind the adapter boundary.

## OYP-602 — Source registry

`SourceRegistry` owns a collection of `Arc<dyn MediaSource>`, validates incoming HTTP(S) URLs, selects the first adapter whose `can_handle` accepts the URL, and returns the typed `UnsupportedSource` error when no adapter matches. Its public contract exposes only generic core types; no provider-specific type is required by callers.

## OYP-603 — deterministic direct fixture adapter

`DirectFixtureSource` is a non-YouTube adapter backed by deterministic `FixtureMedia` entries. It resolves metadata, supplies a normalized directly-playable H.264/AAC MP4 format, emits a curated 720p choice, and creates a generic `DownloadPlan`. Unit coverage proves provider-independent URL recognition, while the repository's deterministic E2E fixture flow uses the same source abstraction without a live external media service.

## Qualification

The implementation is covered by Rust unit/E2E tests in the normal CI matrix. Closeout requires exact-head CI for the commit containing this audit/TODO reconciliation; that run is recorded in the TODO evidence after merge.
