# RMD-905 Audio Track Controls Reconciliation — 2026-09-29

RMD-905 requires the player to expose actual available audio tracks when multiple tracks are supported, connect UI selection to Media3 track selection, and hide or disable the control when only one track exists.

## Production path

- `LocalPlaybackPolicy.availableAudioLabels(LocalPlaybackAsset)` derives player audio labels from validated persisted local `LocalAudioTrack` entries, never from preview data.
- `LocalPlaybackPolicy.shouldEnableAudioSelection(...)` enables the control only when more than one validated audio track exists.
- `PortraitPlayerScreen` renders the audio control from those validated labels and disables it unless the multiple-track policy is true.
- Audio selection mutates the canonical `MediaController.trackSelectionParameters` via `setPreferredAudioLanguage(...)`, so the UI command targets the service-owned Media3 session player rather than decorative Compose-only state.
- Local audio track validation rejects blank and remote paths; no network URI is introduced by audio-track selection.

## Deterministic qualification

`AudioPlaybackControlIntegrationTest` guards the production audio-label/control/Media3-selection wiring off-emulator, and `LocalPlaybackPolicyTest` verifies multiple-track labels plus disabled behavior for zero or one available track. Existing Android smoke/Compose qualification continues to exercise the production player surface on emulator.

RMD-905 must remain unchecked in the canonical TODO until this exact implementation head passes the required exact-head CI matrix and the TODO is reconciled with that evidence.
