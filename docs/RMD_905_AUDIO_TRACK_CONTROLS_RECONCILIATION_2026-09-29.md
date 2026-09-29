# RMD-905 Audio Track Controls Reconciliation — 2026-09-29

RMD-905 requires production playback to expose audio-track controls only when multiple tracks are available and to connect UI selection to Media3 track selection rather than decorative local state.

## Production path

- `LocalPlaybackPolicy.availableAudioLabels(LocalPlaybackAsset)` derives actual audio choices from the validated local playback asset. Labels prefer explicit track labels, then language, then a deterministic track number.
- `LocalPlaybackPolicy.shouldEnableAudioSelection(LocalPlaybackAsset)` enables the control only when more than one audio track is present.
- `PortraitPlayerScreen` displays the audio control from the validated playback asset and disables it when the item has zero or one audio track.
- Activating the audio control cycles through available audio tracks and calls `MediaController.applyAudioSelection(...)` on the canonical session controller.
- `applyAudioSelection` updates `MediaController.trackSelectionParameters` with the selected track language using `setPreferredAudioLanguage(language)`, keeping audio selection on the Media3 session path rather than in decorative Compose state.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/AudioPlaybackControlIntegrationTest.kt` guards the production source wiring for multiple-track gating, label population, cycling behavior, and Media3 preferred-language mutation.

## Boundary

This closes the language-backed audio-track selection wiring for RMD-905. Full Android end-to-end proof remains part of RMD-1502/RMD-1503/RMD-1507 because those tasks must prove completed offline assets cold-start and play through the canonical session under Android runtime conditions.
