# RMD-903 Split A/V Playback Reconciliation — 2026-09-29

RMD-903 requires completed offline items with separate local video and audio assets to play through the canonical Media3 session without introducing network URIs or a second UI-owned player.

## Production path

- `LocalPlaybackPolicy.mediaItemFor(LocalPlaybackAsset)` validates that both the primary video path and optional split audio path are local, distinct, non-blank paths before constructing the `MediaItem` used by `PortraitPlayerScreen`.
- The split audio path is carried as the local `MediaItem` tag by `LocalPlaybackPolicy.mediaItemFor(...)`, preserving the `MediaController.setMediaItem(...)` production path while avoiding remote URI reconstruction in Compose.
- `PlaybackSessionService` now builds the canonical service-owned `ExoPlayer` with `SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this))`.
- `SplitAudioMediaSourceFactory.createMediaSource(...)` builds the canonical video source from the controller-provided `MediaItem`, reconstructs the optional local audio source with `LocalPlaybackPolicy.mediaItemFor(audioPath)`, and returns `MergingMediaSource(videoSource, audioSource)` when split audio exists.
- Single-file completed offline items still use the delegate video source unchanged.

## Deterministic qualification

`LocalPlaybackPolicyTest` already proves split video/audio plans retain distinct local paths, reject remote audio assets, reject identical audio/video paths, and keep all playback requests local. `SplitAudioPlaybackSessionIntegrationTest` now guards that the service-owned canonical player installs the split-audio source factory and that the local playback item carries the split audio path into that production session factory.

RMD-903 must remain unchecked in the canonical TODO until this exact implementation head passes the required exact-head CI matrix and the TODO is reconciled with that evidence.
