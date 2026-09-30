# RMD-601 / RMD-603 current-master reconciliation — 2026-09-29

Candidate exact master: `95fcd880c882ea4cb9c27ce6127ace51db054b87`.

This note reconciles already-merged production wiring before adding redundant Android qualification, following `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`. It does not by itself change the canonical remediation checklist.

## RMD-601 — Library repository wiring

Current production startup no longer supplies `emptyList<LibraryRowModel>()`. `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private `offline-yt-player.sqlite3`, runs startup reconciliation, calls `listLibrary()`, and maps persisted `CoreLibraryItem` records plus `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()` into `LibraryScreenState.Ready` / `LibraryRowModel`. `AppStateRefresher` republishes subsequent repository reads while the Activity is started. Core/open/reconciliation failures map to `LibraryScreenState.Failed`, startup begins in `LibraryScreenState.Loading`, and an empty successful repository read is represented as `Ready(emptyList())` for the Library screen's empty-state rendering.

The mapped production row uses persisted display title, quality, duration, completion state, playback position, and local playback asset paths. Search/filter presentation remains a UI concern over the repository-backed rows rather than fabricated production records.

Relevant production paths:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt`
- `core/src/ffi.rs`
- `core/src/persistence.rs`

Existing behavioral qualification includes the RMD-1402 production Compose Library empty/populated coverage and the RMD-1403/1405 production Compose golden/layout/accessibility coverage.

## RMD-603 — Source-analysis repository/use case

Production Add analysis is centralized through `GeneratedUniffiSourceAnalysisGateway`. As already recorded under RMD-702, the gateway normalizes input through the shared `SupportedUrlPolicy`, invokes the generated `FfiYouTubeSourceService.resolve/listChoices` production boundary, maps provider output through `SourceMetadataPolicy`, and returns structured provider-neutral analysis results rather than preview fixtures. The production Add flow executes analysis off the main thread and cancels/suppresses superseded requests before rendering their result.

The resulting UI state distinguishes successful resolved metadata from structured invalid/unsupported/network/source-change failures through the app-owned gateway boundary. This is the same production analysis path exercised by the RMD-1402 Add behavioral Compose coverage and the deterministic RMD-1500 incremental Add/Share pipeline fixture.

Relevant production paths:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt`
- `core/src/ffi_source.rs`
- `core/src/source.rs`
- `core/src/youtube_source.rs`

## Exact-head qualification

Exact master `95fcd880c882ea4cb9c27ce6127ace51db054b87` passed the complete currently configured exact-head matrix before this reconciliation commit:

- CI `36654583862`
- Android smoke `36654583867`
- Android FGS timeout `36654583922`
- Supply chain `36654583885`
- CI evidence `36654583893`
- Deterministic E2E fixture `36654583870`

Canonical TODO checkboxes should only be changed after the reconciliation commit itself has passed exact-head CI, preserving RMD-G04.
