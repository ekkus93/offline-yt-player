# RMD-601 Library Repository Reconciliation — 2026-10-07

## Scope

This note reconciles current `master` evidence for RMD-601 in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` without weakening the remaining RMD-603, RMD-604, RMD-900, RMD-1000, RMD-1500, or RMD-1800 requirements.

RMD-601 requires production Library state to stop using hard-coded `emptyList<LibraryRowModel>()`, observe list/search/detail state from the repository boundary, map persisted metadata/assets into UI row models, and expose loading, empty, error, and populated states.

## Current production implementation

Current `master` opens the app-private SQLite-backed `GeneratedUniffiCoreGateway` in `MainActivity.bootstrapProductionUi()` and obtains Library data through `openedCore.listLibrary()` instead of hard-coded preview or empty production data.

The same bootstrap path opens `GeneratedUniffiLibraryPlaybackGateway` and `DownloadPresentationGateway`, runs startup reconciliation before initial repository reads, and converts the returned `CoreGatewayResult<List<CoreLibraryItem>>` into `LibraryScreenState` using `toLibraryScreenState(libraryRoot, playbackAssets)`.

`toLibraryScreenState(...)` maps persisted/generated core item fields into `LibraryRowModel`: durable `itemId`, bounded `displayTitle`, quality/duration detail text, completed/playable state, local playback asset paths, and persisted resume position. Playback assets are resolved from `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()` so completed rows reflect local playable asset availability rather than fabricated UI state.

`AppStateRefresher` is the lifecycle-controlled observable-state bridge for the blocking core repository. It refreshes Library state off the Android UI thread, forwards the current Library search query to `gateway.listLibrary(query)`, and republishes refreshed Library and Downloads state to Compose through callbacks controlled by `MainActivity.onStart()/onStop()`.

The Library surface starts as `LibraryScreenState.Loading`, maps core/open/startup failures to `LibraryScreenState.Failed(...)`, and maps successful repository reads to `LibraryScreenState.Ready(...)`. Empty and populated display behavior is owned by the existing Library screen state rendering and UI qualification under RMD-1402/RMD-1403 through RMD-1405.

## Behavioral qualification

`app/src/test/java/com/ekkus/offlineytplayer/AppStateRefresherTest.kt` proves that starting the refresher publishes durable Library and Download state from `FakeCoreGateway`, forwards the observed Library query to the repository boundary, and does not duplicate refresh work across repeated start/stop calls.

The production UI behavior remains covered by the existing Android smoke / Compose behavior / golden qualification recorded under RMD-1402 through RMD-1405. The current exact master `5491de3f5d7c7586455ec90f4d25611f514a8350` passed all six exact-head workflows before this reconciliation note: CI `37665088001`, Android smoke `37665088009`, Android FGS timeout `37665088000`, Supply chain `37665088004`, CI evidence `37665088014`, and Deterministic E2E fixture `37665088002`.

## Checklist impact

This evidence is sufficient to check the four RMD-601 subtasks:

- Replace `emptyList<LibraryRowModel>()` production data with repository-backed state.
- Implement list/search/detail observation.
- Map persisted metadata/assets to UI models.
- Provide loading/empty/error/populated states.

This note does not close RMD-603 source-analysis repository/use-case architecture, RMD-604 lifecycle-aware ViewModel architecture, RMD-1001 full Library UX, RMD-1002 canonical playback launch, Library details/rename/remove actions, any RMD-1500 E2E requirement, or final RMD-1800 closeout.
