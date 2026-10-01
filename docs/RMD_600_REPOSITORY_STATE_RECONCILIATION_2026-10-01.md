# RMD-600 Repository and App-State Reconciliation — 2026-10-01

## Scope

This note reconciles current-master implementation evidence for the remaining RMD-600 repository/state items:

- RMD-601 — Library repository wiring.
- RMD-603 — Source-analysis repository/use case.
- RMD-604 — ViewModel architecture or equivalent lifecycle-aware state holder.

RMD-602 was already reconciled separately in `docs/RMD_602_DOWNLOAD_REPOSITORY_RECONCILIATION_2026-09-27.md`. This note does not claim RMD-1500 end-to-end closeout or external YouTube/service-policy/legal approval.

## Current-master implementation evidence

Exact implementation head audited: `c2a649899052256a85e173d87da6e555b13e7a21`.

### RMD-601 — Library repository wiring

Production library state is repository-backed rather than hard-coded:

- `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private `offline-yt-player.sqlite3` database and initializes `libraryState` from `openedCore.listLibrary()`.
- `AppStateRefresher` periodically calls `gateway.listLibrary(libraryQuery())` off the UI thread and republishes the result into production Compose state while the activity is started.
- `MainActivity.toLibraryScreenState(...)` maps persisted `CoreLibraryItem` and `CoreLibraryPlaybackAsset` records into `LibraryRowModel` values with source-derived display title, duration/quality detail, completed/playable state, local playback paths, and resume position.
- `LibraryScreen` supports loading, ready, unavailable, and failed states; it renders empty and populated repository states distinctly.
- Library search is wired through `onLibraryQueryChanged` in `MainActivity` into `AppStateRefresher.libraryQuery`, and `LibraryScreen` also applies local visible-row filtering while the repository query catches up.
- Library detail reads are wired through `AppLibraryDetailsGateway` and rendered by `LibraryDetailsPanel`, including source identity, duration, size, managed assets, integrity metadata, and resume position.
- List/grid presentation is driven by durable `AppSettingsSnapshot.libraryLayout` and updated through the settings mutation path.

Test evidence includes `LibraryRepositoryGatewayContractTest`, `AppStateRefresherTest`, `LibraryOperationalScreenTest`, and production Compose/golden/layout coverage in the Android smoke lane.

### RMD-603 — Source-analysis repository/use case

Source analysis is centralized outside Compose widgets:

- `SourceAnalysisUseCase` normalizes input through `SupportedUrlPolicy.normalizeSupportedUrl(...)` before calling the gateway.
- The use case calls the production `AppSourceAnalysisGateway` and exposes explicit `SourceAnalysisState` values: `Idle`, `Loading`, `Resolved`, `Unsupported`, `NetworkFailure`, `SourceChanged`, `Failed`, and `Superseded`.
- `begin(...)`, `complete(...)`, and `cancelActive()` maintain a ticketed active request so stale analysis results cannot overwrite newer UI state.
- `AddScreen` consumes this use case, runs blocking source analysis on `Dispatchers.IO`, cancels superseded work when input changes, and renders resolved metadata/options or structured error state.

Test evidence includes `SourceAnalysisUseCaseTest` and `AddScreenPolicyTest`.

### RMD-604 — Lifecycle-aware state holder

The production architecture uses app-owned gateways plus lifecycle-controlled state holders rather than keeping blocking FFI/network work directly in composables:

- `MainActivity` owns the generated core/source/library/download gateway lifecycles and closes them from `onDestroy()`.
- `AppStateRefresher` is a lifecycle-controlled observable-state bridge for blocking repository reads; `onStart()` starts it, `onStop()` stops it, and `onDestroy()` closes it.
- `AppStateRefresher` runs on a single daemon executor, prevents overlapping refreshes with `AtomicBoolean`, and keeps blocking repository reads off the Android main thread.
- `SourceAnalysisUseCase` keeps source-analysis request state, URL validation, error classification, and superseded-request handling outside Compose widgets.
- `AddScreen`, `LibraryScreen`, and `DownloadsScreen` use `rememberSaveable` for relevant UI state such as active destination inputs, library query, selected filter, rename/remove state, and setup/options state; persisted runtime state remains in the core database and settings store.

Test evidence includes `AppStateRefresherTest`, `SourceAnalysisUseCaseTest`, `AddScreenPolicyTest`, and production Compose behavior coverage.

## Qualification evidence

The last fully green implementation head before this documentation sequence was `f0817f12e0c852ea5c96174f6a8a182f02a03dc4`, which passed the full discovered push matrix:

- CI: `36828269272`
- Android smoke: `36828269326`
- Android FGS timeout / API-35 UIDT: `36828269149`
- Supply chain: `36828269478`
- CI evidence: `36828269193`
- Deterministic E2E fixture: `36828269308`

The current documentation head `c2a649899052256a85e173d87da6e555b13e7a21` has already passed CI evidence `36831668297` and Deterministic E2E fixture `36831668251`; the rest of its exact-head push matrix is still running as of this note.

## Canonical TODO impact

RMD-601, RMD-603, and RMD-604 are eligible for checklist reconciliation once the exact-head documentation matrix is green. Do not use this note to close RMD-1500 or final RMD-1800 closeout.
