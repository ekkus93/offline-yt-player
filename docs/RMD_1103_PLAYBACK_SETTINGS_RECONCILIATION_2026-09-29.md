# RMD-1103 Playback Settings Reconciliation — 2026-09-29

RMD-1103 requires retained playback defaults to be durable, applied to the canonical playback session, and limited to settings that have runtime meaning.

## Production path

- `AppSettingsSnapshot` contains durable playback settings for `rememberPlaybackPosition` and `playbackSpeed`; both are persisted by `SharedPreferencesAppSettingsStore` through app-private Android preferences.
- `MainActivity` opens the settings store during production bootstrap, keeps the latest snapshot in Compose state, and wires playback-setting mutations through `settingsStore.update(...)`.
- `PortraitPlayerScreen` consumes the production `AppSettingsSnapshot` rather than decorative local state.
- When `rememberPlaybackPosition` is enabled, the screen derives `configuredStartPositionMs` from the persisted `LocalPlaybackAsset.startPositionMs`, passes it to `MediaController.setMediaItem(...)` before `prepare()`, starts bounded periodic persistence, and persists on teardown through `GeneratedUniffiPlaybackPositionGateway`.
- When `rememberPlaybackPosition` is disabled, playback starts at `0L` and no playback-position writes are performed.
- `playbackSpeed` is applied directly to the canonical Media3 session controller via `MediaController.setPlaybackSpeed(...)` both on initial connection and when the user changes the speed control.
- The speed control immediately persists the next supported value through `onUpdateSettings { playbackSpeed = speed }`, so the setting has a concrete runtime effect and survives reopening through the durable settings store.

## Deterministic qualification

`AppSettingsStoreInstrumentedTest` covers durable settings persistence/reopen for playback speed and remember-position values. `LocalPlaybackPolicyTest` covers restored-position normalization and persistence threshold/reset behavior. `ProductionComposeBehaviorTest.settings_interactions_update_runtime_snapshot` exercises playback settings mutation through the production Compose settings surface, and the RMD-906 playback-position reconciliation covers the canonical session persistence path.

RMD-1103 must remain unchecked in the canonical TODO until this exact evidence head passes the required exact-head CI matrix and the canonical TODO is reconciled with precise evidence.
