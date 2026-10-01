# RMD-1000 Library UX Reconciliation — 2026-10-01

## Scope

This note reconciles current-master implementation evidence for the Library-side RMD-1000 items:

- RMD-1001 — Operational Library screen.
- RMD-1002 — Library Play action.
- RMD-1003 — Library Details action.
- RMD-1004 — Library Rename action.
- RMD-1005 — Library Remove action.

RMD-1006 Downloads screen reconciliation is recorded separately in `docs/RMD_1006_DOWNLOADS_SCREEN_RECONCILIATION_2026-10-01.md`. This note does not claim RMD-1500 end-to-end closeout or external YouTube/service-policy/legal approval.

## Current-master implementation evidence

Exact implementation head audited: `aaa3404116e24b7b4b3caa417e250007af4b2ead`.

### RMD-1001 — Operational Library screen

The Library screen is operational against repository-backed state:

- `MainActivity.bootstrapProductionUi()` maps `GeneratedUniffiCoreGateway.listLibrary()` and `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()` into `LibraryScreenState.Ready` rows.
- `AppStateRefresher` refreshes `gateway.listLibrary(libraryQuery())` off the UI thread while the activity is started.
- `LibraryScreen` renders `Loading`, `Ready`, `Unavailable`, and `Failed` states and has distinct empty and populated Ready-state presentations.
- `LibraryScreen` searches repository-backed title/detail metadata and forwards query changes through `onLibraryQueryChanged`.
- `LibraryItems` implements both `LazyColumn` list mode and `LazyVerticalGrid` grid mode.
- The selected layout comes from durable `AppSettingsSnapshot.libraryLayout`, and the List/Grid toggle mutates durable settings through `onUpdateSettings`.

Test evidence includes `LibraryOperationalScreenTest`, `LibraryRepositoryGatewayContractTest`, `AppStateRefresherTest`, production Compose behavior tests, and golden/layout/accessibility coverage in the Android smoke lane.

### RMD-1002 — Library Play action

`LibraryPlaybackRoute.assetFor(row)` converts a completed repository row with a valid local playback asset into `LocalPlaybackAsset`; incomplete, missing, or remote/corrupt assets fail closed with a user-visible unavailable reason. `LibraryScreen` passes the validated asset through `onPlay`; `OfflineYTPlayerApp` stores that asset as `playbackAsset` and opens `PortraitPlayerScreen`.

Playback itself is now routed through the RMD-900 canonical MediaSession path: `PortraitPlayerScreen` connects to `PlaybackSessionService` with a `MediaController`, and the service owns the canonical Media3/ExoPlayer session. See `docs/RMD_900_MEDIA3_PLAYBACK_RECONCILIATION_2026-10-01.md`.

### RMD-1003 — Library Details action

`LibraryScreen` obtains details through `AppLibraryDetailsGateway.getDetails(row.id)` on `Dispatchers.IO`. `GeneratedUniffiLibraryDetailsGateway` maps the generated `FfiLibraryDetailsService` record into `CoreLibraryDetails` with source provider/media ID, canonical URL, display title, duration, quality, completed state, playback position, total bytes, and managed assets.

`LibraryDetailsPanel` renders local assets, source identity, duration, managed size, subtitle/media asset rows, SHA/integrity metadata, and recovery-relevant missing-asset detail when supplied by the gateway.

### RMD-1004 — Library Rename action

`LibraryScreen` exposes a bounded rename flow with explicit edit/cancel/save UI and calls `AppLibraryMutationGateway.renameDisplayTitle(row.id, renameTitle)` on `Dispatchers.IO`.

The generated bridge `GeneratedUniffiLibraryMutationGateway` calls `FfiLibraryRenameService.renameDisplayTitle(...)`. Core implementation in `core/src/ffi_library_rename.rs` normalizes whitespace/control characters, rejects empty titles, bounds display titles to `MAX_LIBRARY_TITLE_CHARS`, and performs metadata-only rename without changing owned asset paths.

Tests in `ffi_library_rename.rs` verify metadata-only persistence across reopen, empty-title rejection, title bounding, and missing-item behavior.

### RMD-1005 — Library Remove action

`LibraryScreen` exposes destructive confirmation before removal. Only after the user chooses Confirm remove does it call `AppLibraryMutationGateway.removeLibraryItem(libraryRoot, row.id, confirmed = true)` on `Dispatchers.IO`.

The generated bridge calls `FfiLibraryRemoveService.removeLibraryItem(...)`; core implementation in `core/src/ffi_library_remove.rs` rejects unconfirmed removal, deletes owned assets through `delete_library_item_owned_assets(...)`, then removes metadata and surfaces errors for missing items or failed deletion. RMD-404/RMD-1303 cover the owned-asset deletion/path-safety semantics used here.

The UI reports success or failure and relies on the repository refresher to update the list after durable outcome. Tests in `ffi_library_remove.rs` verify confirmation is required, confirmed removal deletes owned files before metadata, and missing items are reported without creating state.

## Qualification evidence

The last fully green implementation head before this documentation sequence was `f0817f12e0c852ea5c96174f6a8a182f02a03dc4`, which passed the full discovered push matrix:

- CI: `36828269272`
- Android smoke: `36828269326`
- Android FGS timeout / API-35 UIDT: `36828269149`
- Supply chain: `36828269478`
- CI evidence: `36828269193`
- Deterministic E2E fixture: `36828269308`

The current documentation head `aaa3404116e24b7b4b3caa417e250007af4b2ead` has CI evidence `36833230478` already green while the rest of the exact-head push matrix continues.

## Canonical TODO impact

RMD-1001 through RMD-1005 are eligible for checklist reconciliation once exact-head documentation qualification is green. Do not use this note to close RMD-1500 or final RMD-1800 closeout.
