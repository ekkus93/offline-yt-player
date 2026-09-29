# RMD-601 Library Repository Reconciliation — 2026-09-29

## Scope

This reconciliation records the current `master` evidence for RMD-601, which requires replacing production fake/empty Library state with repository-backed list/search/detail observation, persisted metadata/asset mapping, and loading/empty/error/populated states.

## Production implementation evidence

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` opens the generated UniFFI core gateway against the app-private SQLite database during `bootstrapProductionUi()` and initializes `libraryState` from `GeneratedUniffiCoreGateway.listLibrary()` rather than `emptyList<LibraryRowModel>()`.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` maps `CoreLibraryItem` plus `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()` results into `LibraryRowModel`, preserving persisted source identity, title, duration, quality, completed/playable state, video/audio asset paths, and resume position.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` preserves production loading and failure states through `LibraryScreenState.Loading` and `LibraryScreenState.Failed(...)`, and populated/empty repository results through `LibraryScreenState.Ready(...)`.
- `app/src/main/java/com/ekkus/offlineytplayer/AppStateRefresher.kt` refreshes Library state from the production gateway after startup so production UI state follows repository changes instead of a static fixture.
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` exposes generated-binding-agnostic Library repository operations: `listLibrary(query)`, `getLibraryItem(itemId)`, and `deleteLibraryItem(itemId)`, with blocking generated UniFFI calls guarded off the Android main thread.

## Behavioral qualification added in this change

- `app/src/test/java/com/ekkus/offlineytplayer/coregateway/LibraryRepositoryGatewayContractTest.kt` verifies the app-owned Library gateway contract for repository-backed list ordering, bounded search filtering, detail lookup, delete mutation, and missing-item reporting without requiring Android runtime or live provider access.

## Remaining boundaries

This closes the RMD-601 repository-wiring obligation only. Library item actions that are separately called out in RMD-1001 through RMD-1005 remain governed by those sections and must not be marked complete by this reconciliation alone.
