# RMD-906 playback position persistence reconciliation — 2026-09-29

RMD-906 requires playback to load a persisted/restored position before starting an item, persist periodically at bounded cadence, persist on stop/session transitions, apply documented completion-threshold/reset behavior, and qualify restart/resume semantics.

## Current production path

`app/src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt` implements the production playback-position path through the canonical MediaController screen:

- `configuredStartPositionMs` is derived from `LocalPlaybackPolicy.restoredStartPosition(validated.startPositionMs)` when `settings.rememberPlaybackPosition` is enabled, otherwise playback starts at zero.
- The controller calls `setMediaItem(LocalPlaybackPolicy.mediaItemFor(validated), configuredStartPositionMs)` before `prepare()`, so the restored position is applied before starting the item.
- `persistPlaybackPosition(false)` runs through a `periodicSaver` scheduled at `LocalPlaybackPolicy.PositionPersistCadenceMs`.
- `persistPlaybackPosition(true)` runs during `DisposableEffect.onDispose`, covering stop/session teardown transitions.
- Persistence goes through `GeneratedUniffiPlaybackPositionGateway.open(databasePath).savePlaybackPosition(...)`, keeping the saved position in the app's generated-core persistence boundary.

`app/src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt` provides the policy boundary:

- `PositionPersistCadenceMs = 5_000L` bounds periodic saves.
- `NearEndCompletedThresholdMs = 30_000L` resets near-complete playback to zero.
- `restoredStartPosition`, `shouldPersistPosition`, and `persistedPositionForStop` clamp invalid positions and normalize completion behavior.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt` already covers restored-position normalization, bounded-cadence saves, completion reset, and clamped stop semantics.

`app/src/test/java/com/ekkus/offlineytplayer/playback/PlaybackPositionPersistenceIntegrationTest.kt` adds a production source guard proving the Compose playback screen applies the restored position before prepare, persists periodically, persists on disposal, and saves through the generated UniFFI playback-position gateway.

## Boundary

This closes the deterministic production wiring and policy proof for RMD-906. Full RMD-1501/RMD-1502 end-to-end playback qualification remains separate because it must cold-start the Android app offline and prove completed local playback through the canonical session.
