# RMD-1802 — Library stable-ID metadata refresh invalidation (2026-10-14)

## Independent review finding

The production `LibraryScreen` invalidated expanded Details and in-flight `AppLibraryDetailsGateway.getDetails` callbacks when repository readiness or the *set of row IDs* changed. An existing item could be renamed, repaired, completed, or have its local assets updated while retaining its ID. That refreshed row did not change the old token, so the screen could retain a stale `CoreLibraryDetails` panel or publish an older asynchronous lookup after the refresh.

## Correction

`app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` now derives the repository-generation token from readiness and the complete `LibraryRowModel` list, rather than from ID membership alone. The existing `LaunchedEffect(repositoryToken)` clears expanded Details and mutation controls, and the callback identity guard rejects responses belonging to an older repository snapshot. Durable IDs, mutation targets, and playback assets are not rewritten.

## Behavioral regression coverage

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt` adds two packaged Compose tests:

- `libraryContentRefreshWithStableIdDropsInFlightDetails` blocks a real app-owned gateway call, changes the row's title/detail while retaining the ID, releases the old result, and asserts that stale metadata and loading status never reappear.
- `libraryContentRefreshWithStableIdClearsExpandedDetails` expands authoritative Details, updates the repository row with the same ID, and asserts that the old panel is cleared.

These tests run as part of the existing `ProductionComposeBehaviorTest` class in the API-29 Android smoke lane; no additional emulator matrix or separate PR is required.

## Qualification and closeout

Implementation and regression coverage must pass exact-head Rust/Android CI, Android smoke, FGS timeout, supply-chain, CI evidence, deterministic fixture E2E, Android cold start, and Android real-network E2E. Record the exact SHA and run IDs before reconciling any final checkbox. This finding alone does not complete the independent Rust/Android audit or external service/legal approval.
