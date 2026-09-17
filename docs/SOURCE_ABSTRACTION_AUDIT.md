# OYP-600 Source Abstraction Audit

This audit qualifies OYP-601 through OYP-603 against the portable Rust source boundary.

## OYP-601 — `MediaSource` contract

`core/src/source.rs` defines the provider-neutral `MediaSource` trait. It exposes `can_handle`, asynchronous metadata `resolve`, normalized `formats`, curated `choices`, `download_plan`, and optional/default subtitle and thumbnail hooks. Return values use generic domain records (`MediaInfo`, `MediaFormat`, `QualityChoice`, `DownloadPlan`, `SubtitleTrack`) rather than provider-specific extraction structures.

All five OYP-601 checklist requirements are implemented.

## OYP-602 — Source registry

`SourceRegistry` owns an ordered collection of `Arc<dyn MediaSource>`. `select` validates the incoming HTTP(S) URL, chooses the first adapter whose `can_handle` contract accepts it, and returns typed `UnsupportedSource` when no adapter accepts the URL. The registry surface exposes only the `MediaSource` trait and generic domain types, preventing provider-specific structures from entering generic UI/domain APIs.

The unit test `registry_rejects_unknown_sources` qualifies the unsupported-source boundary. All three OYP-602 checklist requirements are implemented.

## OYP-603 — Direct/local fixture adapter

`DirectFixtureSource` is explicitly provider-independent and deterministic. Its configured fixture records resolve to normalized `MediaInfo`, a directly playable combined H.264/AAC format, curated quality choice, and generic `DownloadPlan`. It validates media URLs and sanitizes titles, media IDs, filenames, and relative paths through the same generic domain/security boundaries used by production adapters.

`fixture_adapter_is_provider_independent` proves the adapter does not accidentally claim YouTube URLs. `FfiSourceService::with_fixtures` then drives resolve and choice-list operations through the same adapter in CI without contacting a live provider. Its tests qualify successful coarse FFI resolution plus typed unsupported/canceled failures. Download-engine tests independently use loopback fixtures, so the source/download qualification stack does not require external services.

Both OYP-603 checklist requirements are implemented.

## Qualification

The source abstraction is exercised by the workspace Rust suite on every CI run and the coarse source records are additionally covered by the UniFFI Kotlin/Android ABI job. The latest exact-head master qualification before this audit was CI `35215157544`, which passed at `32457a5afa0d50a64d91770dccd8b277a66c5b6b`; subsequent exact-head CI for this audit must also pass before merge.
