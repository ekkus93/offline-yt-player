# Source abstraction audit

This audit maps the portable source implementation to OYP-601 through OYP-603 without claiming work that is not present.

## OYP-601 — `MediaSource` contract

`core/src/source.rs` defines the provider-neutral `MediaSource: Send + Sync` contract with `id`, `can_handle`, asynchronous `resolve`, normalized `formats`, curated `choices`, and `download_plan`. `MediaInfo` carries normalized subtitle tracks and an optional thumbnail URL, so resolved metadata can already advertise those assets without provider-specific types.

The TODO's explicit optional subtitle/thumbnail *hook* requirement remains open: the trait does not currently expose separate hook methods for fetching or planning those optional assets.

## OYP-602 — Source registry

`SourceRegistry` owns `Arc<dyn MediaSource>` values, validates an HTTP(S) URL before adapter selection, selects the first adapter whose `can_handle` accepts the URL, and returns typed `UnsupportedSource` when no adapter matches. Its public boundary uses only portable domain types; provider implementation types do not leak into callers.

The existing registry test proves clean unsupported-source behavior. The `MediaSource` trait itself is the provider-isolation boundary.

## OYP-603 — Direct/local fixture adapter

`DirectFixtureSource` is a deterministic, non-YouTube adapter. It resolves registered fixture URLs into normalized `MediaInfo`, emits a curated 720p choice, and builds a sanitized portable `DownloadPlan`. The deterministic E2E suite uses this provider-independent fixture path together with a local HTTP server, so CI does not require live YouTube or another external service.

## Reconciliation guidance

OYP-602 and OYP-603 can be reconciled complete from current implementation and tests. For OYP-601, `can_handle`, metadata resolution, normalized-format enumeration, and download-plan creation can be marked complete; the explicit optional subtitle/thumbnail hook checkbox should remain open until those hook methods are added to the contract.
