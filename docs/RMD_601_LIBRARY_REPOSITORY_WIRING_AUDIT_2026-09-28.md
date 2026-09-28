# RMD-601 Library Repository Wiring Audit — 2026-09-28

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

This note records current-master evidence and remaining gaps for RMD-601. It is intentionally not a closeout note and does not mark RMD-601 complete.

## Current evidence

`MainActivity.bootstrapProductionUi()` no longer supplies hard-coded empty Library data. It opens `GeneratedUniffiCoreGateway` against the app-private SQLite database, calls `listLibrary()`, combines that result with `GeneratedUniffiLibraryPlaybackGateway.listPlaybackAssets()`, and maps persisted records into `LibraryScreenState` through `toLibraryScreenState(...)`.

`AppStateRefresher` keeps Library state live while the activity is started. It calls `gateway.listLibrary()` off the UI thread on a bounded cadence and publishes the result through the production `onLibrary` callback. `MainActivity` then maps the refreshed result into Compose state.

The current mapping includes persisted item identity, bounded display title, detail text derived from persisted quality/duration/playback availability, completed/playable state, local video/audio asset paths, and persisted playback position.

Relevant production files:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/AppStateRefresher.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGateway.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiLibraryPlaybackGateway.kt`

## Remaining RMD-601 gaps

RMD-601 is still not complete because the canonical checklist also requires list/search/detail observation and full loading/empty/error/populated state proof. Current master has evidence for repository-backed list observation and error mapping, but not enough unredacted current-master evidence to claim all of the following are complete:

- Library search against actual persisted records.
- A real detail observation path for selected Library items.
- Behavioral proof for loading, empty, error, and populated Library states as RMD-601-specific repository wiring evidence.

The bridge currently redacts the production UI file containing Library/Downloads Compose surfaces because it includes URL fixture text, so this audit does not cite that file as sufficient evidence. RMD-601 should remain unchecked until the missing search/detail/state proof is implemented and qualified or separately verified from unredacted current-master source.

## Canonical TODO reconciliation intent

Do not mark any RMD-601 checkbox complete from this audit alone. A later implementation/evidence pass may close only the specific subtasks that have production wiring, behavioral tests, exact implementation SHA, and exact-head CI evidence.
