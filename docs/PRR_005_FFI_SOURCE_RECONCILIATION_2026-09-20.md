# PRR-005 — Production source FFI reconciliation

PRR-005 from `docs/POST_REMEDIATION_CODE_REVIEW_2026-09-20.md` is repaired by the production source boundary now present on `master`.

## Production/fixture separation

`core/src/ffi_source.rs` intentionally retains `FfiSourceService::with_fixtures(...)` for deterministic fixture tests, but production Android does not use that fixture service. The same module defines the distinct `FfiYouTubeSourceService`, whose constructor owns `YouTubeSource::default()` and whose `resolve`/`list_choices` operations return provider-neutral UniFFI records.

`app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppSourceAnalysisGateway.kt` opens `FfiYouTubeSourceService`, not `FfiSourceService`. `MainActivity` lifecycle-owns that gateway and injects it into the Compose Add flow. `AddScreen` dispatches `gateway.analyze(url)` on `Dispatchers.IO`; it no longer uses fixture preview data as production resolution.

## Behavioral evidence

Rust unit coverage in `core/src/ffi_source.rs` proves the production service rejects non-YouTube input without network access and honors cancellation before provider I/O. The live provider itself has deterministic fixture/parser coverage plus the bounded manual live-source workflow in `.github/workflows/youtube-live-smoke.yml`.

The production Android wiring was qualified on exact head `077a50f2efbc294b68e48ef79305bdcb0f650c2f` by push CI `35502421682` and pull-request CI `35502828936`, then merged as PR #238. Post-merge `master` SHA `e1998934daed6e130da20021df2cd2dc51be9308` passed CI run `35503076029`.

This reconciliation does not approve the separate external YouTube/service-policy/legal release gate.
