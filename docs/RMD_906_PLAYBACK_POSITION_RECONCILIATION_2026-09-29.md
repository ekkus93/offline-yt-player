# RMD-906 Playback Position Persistence Reconciliation — 2026-09-29

RMD-906 requires persisted playback position to be loaded before starting a completed local item, saved periodically at bounded cadence and on session teardown, reset according to the documented completion threshold, and covered by deterministic restart/resume policy tests.

## Production path

- `LocalPlaybackAsset.startPositionMs` carries the persisted library position into the local playback boundary and `LocalPlaybackPolicy.restoredStartPosition(...)` clamps invalid negative state.
- `PortraitPlayerScreen` computes `configuredStartPositionMs` from the persisted asset when `settings.rememberPlaybackPosition` is enabled and passes that position to `MediaController.setMediaItem(...)` before `prepare()`.
- The production player persists through `GeneratedUniffiPlaybackPositionGateway` on a dedicated background executor against the app-private `offline-yt-player.sqlite3` database; playback persistence does not block the Compose/main thread.
- A bounded periodic saver runs at `LocalPlaybackPolicy.PositionPersistCadenceMs` (5 seconds). `shouldPersistPosition(...)` suppresses writes until meaningful position movement occurs, while still allowing completion to be recorded.
- `DisposableEffect.onDispose` removes the periodic callback and performs a final persistence transition before releasing the controller/executor.
- `LocalPlaybackPolicy.persistedPositionForStop(...)` resets the stored position to zero when playback is within `NearEndCompletedThresholdMs` (30 seconds) of the known duration; otherwise it persists the normalized current position.
- The persisted library model already exposes `playbackPositionMs`, so a cold/restarted UI can reconstruct the same `LocalPlaybackAsset.startPositionMs` instead of relying on service-local state.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt` proves restored-position normalization, bounded-cadence persistence, completion/reset behavior, position clamping, and persisted library identity. Production source wiring in `app/src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt` proves the restored position is applied to the canonical `MediaController` before prepare and that periodic/final persistence uses the generated core gateway.

## Exact-head evidence

The complete six-workflow matrix passed on exact master `aeef87e1feda6cf4ec8c695a9ffb4aefec84293a`: CI `36645508680`, Android smoke `36645508675`, Android FGS timeout `36645508711`, Supply chain `36645508740`, CI evidence `36645508674`, and Deterministic E2E fixture `36645508707`.

The same playback-position implementation is present on exact master `3a4d92a249405754a0c678bae7d60d1ba155b48c`, which passed the required six-workflow matrix: CI `36652907080`, Android smoke `36652907084`, Android FGS timeout `36652907127`, Supply chain `36652907103`, CI evidence `36652907102`, and Deterministic E2E fixture `36652907106`.

This evidence satisfies the RMD-906 implementation and qualification requirements. The canonical TODO can be reconciled by checking the RMD-906 subtasks and referencing this document plus the exact-head matrix above.
