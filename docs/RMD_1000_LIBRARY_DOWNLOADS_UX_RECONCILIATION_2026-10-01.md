# RMD-1000 Library and Downloads UX Reconciliation Evidence — 2026-10-01

This evidence note records current-`master` implementation and qualification evidence for the RMD-1000 Library and Downloads UX items that are already implemented in production paths but still need canonical TODO reconciliation. It does not replace `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Candidate SHA and exact-head qualification basis

Baseline implementation audited from current `master` before this note: `3c9c99bb6db56f58ff5ec67a007e4302d7e86a68`.

That exact SHA passed the full six-workflow matrix:

- CI: `36824892374` — success.
- Android smoke: `36824892241` — success.
- Android FGS timeout / API-35 UIDT qualification: `36824892217` — success.
- Supply chain: `36824892211` — success.
- CI evidence: `36824892166` — success.
- Deterministic E2E fixture: `36824892265` — success.

This document commit must receive its own exact-head CI before the canonical TODO cites it as merged evidence.

## RMD-1001 — Operational Library screen

Current production Library UI is operational and repository-backed:

- `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private `offline-yt-player.sqlite3` database and publishes initial `LibraryScreenState` from `listLibrary()`.
- `AppStateRefresher` lifecycle-starts/stops repository refresh and calls `gateway.listLibrary(libraryQuery())` off the Android main thread.
- `MainActivity.toLibraryScreenState(...)` maps persisted `CoreLibraryItem` records plus `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()` results into `LibraryRowModel` values with title, duration, quality, completion, local video/audio paths, and resume position.
- `LibraryScreen` renders loading, empty, failed, populated, list, grid, search, unavailable-repository, detail, rename, and remove states.
- Search uses the current repository/query path through `onLibraryQueryChanged` plus local row matching for immediate UI filtering.
- List/grid selection is backed by durable `AppSettingsSnapshot.libraryLayout` and mutates `AppSettingsMutation.libraryLayout` rather than a transient/decorative flag.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/AppStateRefresher.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/AppStateRefresherTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/LibraryOperationalScreenTest.kt`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## RMD-1002 — Library Play action

Current production Library Play routes completed local Library rows into the canonical playback path:

- `LibraryPlaybackRoute.assetFor(row)` constructs a `LocalPlaybackAsset` only for completed rows with a valid local video path.
- `LibraryPlaybackRoute.unavailableReason(row)` disables and explains incomplete rows, completed rows missing a local video asset, and invalid/remote playback paths.
- `OfflineYTPlayerApp` passes the selected `LocalPlaybackAsset` into `PortraitPlayerScreen`, which uses the MediaSession/MediaController playback path.
- `ProductionComposeBehaviorTest.player_entry_exposes_transport_and_track_controls` exercises the Library Play path through production Compose and verifies the Player screen exposes transport/track controls.
- `LibraryOperationalScreenTest.libraryPlayRouteExplainsUnavailableItems` covers fail-closed incomplete/missing/remote rows and valid local playback rows.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/LibraryOperationalScreenTest.kt`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## RMD-1003 — Library Details action

Current production Library Details is real, not a placeholder:

- `GeneratedUniffiLibraryDetailsGateway` opens generated `FfiLibraryDetailsService` and calls `libraryDetails(itemId)` off the Android main thread.
- `FfiLibraryDetailsService` reads persisted Library metadata from `LibraryStore` and returns source provider/media id, canonical URL, title, duration, quality, completion, playback position, total bytes, and all managed local assets with relative path, MIME, byte size, kind, and SHA-256 presence.
- `LibraryScreen` renders a details panel with local assets, source identity, canonical source, duration, size, resume position, subtitle/thumbnail/audio/video asset metadata, and integrity metadata.
- `ProductionComposeBehaviorTest.library_details_action_reads_repository_detail_gateway_off_main_thread` exercises the UI action through `AppLibraryDetailsGateway`.
- Rust unit test `details_include_source_assets_sizes_subtitles_and_integrity_metadata` verifies source, subtitle asset, size, and integrity metadata.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppLibraryDetailsGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `core/src/ffi_library_details.rs`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## RMD-1004 — Library Rename action

Current production Library Rename is metadata-only, bounded, and tested:

- `GeneratedUniffiLibraryMutationGateway.renameDisplayTitle(...)` calls generated `FfiLibraryRenameService.renameDisplayTitle(...)` off the Android main thread.
- `FfiLibraryRenameService` normalizes whitespace/control characters, rejects empty titles, and bounds display titles to `MAX_LIBRARY_TITLE_CHARS`.
- Rename mutates persisted display metadata only and does not rename managed files.
- `LibraryScreen` exposes a bounded rename flow and reports mutation failures/success without assuming a local-only fake.
- `ProductionComposeBehaviorTest.library_rename_and_remove_actions_use_repository_mutations_after_confirmation` exercises the UI mutation gateway path.
- Rust tests verify metadata-only persistence across reopen, empty-title rejection, long-title bounds, and missing-item handling.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppLibraryMutationGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `core/src/ffi_library_rename.rs`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## RMD-1005 — Library Remove action

Current production Library Remove is confirmed, durable, and uses the core owned-asset deletion path:

- `GeneratedUniffiLibraryMutationGateway.removeLibraryItem(...)` calls generated `FfiLibraryRemoveService.removeLibraryItem(libraryRoot, itemId, confirmed)` off the Android main thread.
- `FfiLibraryRemoveService` rejects unconfirmed destructive removal before touching files or metadata.
- Confirmed removal delegates to `delete_library_item_owned_assets(...)`, deleting owned video/audio/subtitle/thumbnail/partial assets through the safe path lifecycle and then removing metadata.
- `LibraryScreen` requires destructive confirmation before invoking the remove gateway and surfaces partial failure/error messages.
- `ProductionComposeBehaviorTest.library_rename_and_remove_actions_use_repository_mutations_after_confirmation` proves the UI sends confirmed removal to the mutation gateway.
- Rust tests verify unconfirmed removal is non-mutating, confirmed removal deletes owned files and metadata, and missing item removal is reported without creating records.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppLibraryMutationGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `core/src/ffi_library_remove.rs`
- `core/src/deletion.rs`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## RMD-1006 — Operational Downloads screen

Current production Downloads UI is durable-queue-backed and action-gated:

- `MainActivity.bootstrapProductionUi()` obtains initial download queue state from `GeneratedUniffiCoreGateway.listDownloadQueue()`.
- `AppStateRefresher` refreshes the durable queue off-main and publishes `DownloadsScreenState`.
- `MainActivity.toDownloadsScreenState(...)` maps durable `CoreDownloadSnapshot` values into `DownloadRowModel` values with durable state, transferred/total bytes, progress percentage, sanitized error text, and title presentation.
- `DownloadsScreen` renders durable rows, applies `DownloadScreenPolicy.Filters`, and exposes only legal pause/resume/cancel/retry actions for the current durable state.
- `DownloadRowControlBinding.invoke(...)` routes UI actions through `AppDownloadControlGateway`, which is the same durable control boundary used elsewhere.
- `DownloadsOperationalScreenTest` covers filters, illegal action suppression for completed items, progress/state/error/speed/ETA presentation, and per-state legal actions.
- `ProductionComposeBehaviorTest.downloads_actions_invoke_the_real_control_boundary` exercises the real UI control boundary.

Implementation/test paths:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/AppStateRefresher.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadsOperationalScreenTest.kt`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`

## Canonical TODO follow-up

After this document commit passes exact-head CI, the canonical remediation TODO can mark the RMD-1001 through RMD-1006 subtasks complete with this document and exact run IDs as evidence, while keeping broader RMD-1500 E2E and RMD-1800 final closeout unchecked.
