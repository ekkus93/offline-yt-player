# RMD-904 Subtitle Playback Controls Reconciliation — 2026-09-29

RMD-904 requires persisted local subtitle tracks to be attached to offline playback, actual available tracks to be exposed in the player UI, and selection/disable actions to mutate the canonical Media3 session player rather than only changing decorative Compose state.

## Production path

- `LocalPlaybackPolicy.mediaItemFor(LocalPlaybackAsset)` converts every persisted `LocalSubtitleTrack` into a local-file `MediaItem.SubtitleConfiguration`; remote subtitle paths are rejected and only supported subtitle MIME types are accepted.
- `PortraitPlayerScreen` derives its subtitle labels from the validated persisted playback asset.
- Subtitle actions now mutate the `MediaController.trackSelectionParameters` used by the canonical `PlaybackSessionService` player. Selecting a track enables text and sets that persisted track's language; cycling after the final track disables `C.TRACK_TYPE_TEXT` and renders `Subtitles: Off`.
- No network URI is introduced by subtitle playback.

## Deterministic qualification

`SubtitlePlaybackControlIntegrationTest` guards the production attachment and controller-selection wiring off-emulator, consistent with the Android qualification acceleration plan. Existing Android smoke/Compose qualification continues to exercise the production player surface on emulator.

## Exact-head evidence

Exact master `3a4d92a249405754a0c678bae7d60d1ba155b48c` passed the required six-workflow matrix: CI `36652907080`, Android smoke `36652907084`, Android FGS timeout `36652907127`, Supply chain `36652907103`, CI evidence `36652907102`, and Deterministic E2E fixture `36652907106`.

This evidence satisfies the RMD-904 implementation and qualification requirements. The canonical TODO can be reconciled by checking the RMD-904 subtasks and referencing this document plus the exact-head matrix above.
