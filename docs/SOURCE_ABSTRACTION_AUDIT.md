# OYP-600 Source Abstraction Audit

OYP-601 through OYP-603 require a provider-neutral source contract, registry dispatch, and deterministic non-YouTube adapter suitable for CI. The current Rust core implements that boundary in `core/src/source.rs`.

## OYP-601 — `MediaSource` contract

`MediaSource` is a `Send + Sync` provider abstraction with `can_handle`, asynchronous metadata resolution, normalized format enumeration, curated quality choices, download-plan creation, and optional/default subtitle and thumbnail hooks. The contract exchanges portable domain models (`MediaInfo`, `MediaFormat`, `QualityChoice`, `DownloadPlan`, `SubtitleTrack`) rather than provider-specific structures.

## OYP-602 — Source registry

`SourceRegistry` owns an ordered collection of `Arc<dyn MediaSource>` adapters. `select` validates the URL before dispatch, chooses the first adapter whose `can_handle` accepts it, and returns the typed `UnsupportedSource` error when no adapter matches. Provider-specific types therefore remain behind the trait and do not leak through generic UI/domain APIs.

## OYP-603 — Direct/local fixture adapter

`DirectFixtureSource` is explicitly provider-independent and deterministic. Tests supply an in-memory fixture map; the adapter resolves sanitized metadata, exposes a normalized directly playable combined format and curated 720p choice, and creates a validated `DownloadPlan`. It has no YouTube, DNS, credential, or live-service dependency and is already used by coarse FFI/source qualification.

## Existing automated qualification

`source::tests::registry_rejects_unknown_sources` qualifies clean unsupported-source behavior. `source::tests::fixture_adapter_is_provider_independent` proves deterministic fixture dispatch and rejects accidental YouTube coupling. Existing FFI fixture tests exercise resolve/list-choice behavior through this adapter, while download fixture tests qualify provider-independent plans and transfers against loopback HTTP.

## Result

All explicit OYP-601, OYP-602, and OYP-603 checklist requirements are implemented. Exact-head CI for this audit commit is the closeout evidence; the canonical TODO should be reconciled after that qualification passes and the audit is merged.
