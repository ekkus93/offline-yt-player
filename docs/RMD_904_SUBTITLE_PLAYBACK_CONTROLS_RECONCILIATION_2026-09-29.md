# RMD-904 Subtitle Playback Controls Reconciliation — 2026-09-29

RMD-904 requires persisted local subtitle tracks to be attached to offline playback, actual available tracks to be exposed in the player UI, and selection/disable actions to mutate the canonical Media3 session player rather than only changing decorative Compose state.

## Production path

- `LocalPlaybackPolicy.mediaItemFor(LocalPlaybackAsset)` converts every persisted `LocalSubtitleTrack` into a local-file `MediaItem.SubtitleConfiguration`; remote subtitle paths are rejected and only supported subtitle MIME types are accepted.
- `PortraitPlayerScreen` derives its subtitle labels from the validated persisted playback asset.
- Subtitle actions now mutate the `MediaController.trackSelectionParameters` used by the canonical `PlaybackSessionService` player. Selecting a track enables text and sets that persisted track's language; cycling after the final track disables `C.TRACK_TYPE_TEXT` and renders `Subtitles: Off`.
- No network URI is introduced by subtitle playback.

## Deterministic qualification

`SubtitlePlaybackControlIntegrationTest` guards the production attachment and controller-selection wiring off-emulator, consistent with the Android qualification acceleration plan. Existing Android smoke/Compose qualification continues to exercise the production player surface on emulator.

RMD-904 must remain unchecked in the canonical TODO until this exact implementation head passes the required exact-head CI matrix and the TODO is reconciled with that evidence.
