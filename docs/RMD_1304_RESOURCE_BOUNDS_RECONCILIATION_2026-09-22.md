# RMD-1304 Provider/resource bounds reconciliation

This document records the implementation and qualification evidence for `RMD-1304 — Provider/resource bounds` from `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

The canonical remediation TODO remains the source of completion truth. This evidence document exists to preserve the exact implementation and CI trail before the canonical TODO checkbox reconciliation is performed.

## Scope

RMD-1304 requires bounds and tests for:

- provider response size;
- URL and metadata lengths;
- redirects, timeouts, and asset sizes;
- concurrent downloads;
- retry attempts;
- tests for each enforced limit.

## Implementation evidence

The merged implementation is PR #316, merged to `master` as `da8ea3050809a975cada87f8ad785e236783c94d` from exact implementation head `a21040f9745e3cc943178f7723b7d193e1f04ae7`.

Primary implementation areas:

- `core/src/resource_bounds.rs` centralizes provider response, provider URL, metadata, redirect, and timeout bounds.
- `core/src/youtube_source.rs` routes production YouTube parsing and fetch behavior through the shared bounds: declared and observed watch-response size, media/caption/thumbnail URL length, title/subtitle metadata lengths, stream/subtitle collection counts, redirect count, and provider HTTP timeouts.
- `core/src/download.rs` retains `DownloadPolicy.max_asset_bytes` enforcement for declared and observed transfer sizes.
- `core/src/concurrency.rs` retains `MAX_CONCURRENT_DOWNLOADS`, `bounded_download_concurrency`, and `DownloadConcurrencyGate` as the core-enforced concurrency ceiling.
- `core/src/worker.rs` consumes `DownloadPolicy.max_attempts` as the bounded retry-attempt policy for download work.

## Behavioral qualification evidence

Deterministic Rust tests cover the non-runtime proof requested by `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`:

- `core/src/resource_bounds.rs` tests provider response-size rejection, provider URL bound rejection, title truncation, provider redirect/timeout policy, and the core asset/concurrency/retry policy constants.
- `core/src/youtube_source.rs` tests bounded stream/subtitle/thumbnail/metadata parsing, oversized provider URL rejection, and invalid caption metadata rejection.
- Existing `core/src/concurrency.rs` tests prove the core concurrency ceiling and preference clamping.
- Existing `core/src/download.rs` tests cover transfer size enforcement and transfer-bound behavior.

## CI evidence

- Push CI `35800979863` passed on exact head `a21040f9745e3cc943178f7723b7d193e1f04ae7`.
- PR CI `35801483348` passed on exact head `a21040f9745e3cc943178f7723b7d193e1f04ae7`.
- Post-merge master CI `35801968234` passed on merge commit `da8ea3050809a975cada87f8ad785e236783c94d`.

## Reconciliation result

RMD-1304 has implementation, deterministic tests, exact-head push CI, PR CI, and post-merge master CI evidence. The remaining reconciliation action is to update the canonical remediation TODO checkboxes and evidence paragraph without collapsing or corrupting the detailed TODO.
