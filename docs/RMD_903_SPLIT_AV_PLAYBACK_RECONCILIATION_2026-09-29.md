# RMD-903 Split A/V Playback Reconciliation — 2026-09-29

RMD-903 requires completed offline items with separate local video and audio assets to play through the canonical Media3 session without introducing network URIs or a second UI-owned player.

## Production path

- `LocalPlaybackPolicy.mediaItemFor(LocalPlaybackAsset)` validates that both the primary video path and optional split audio path are local, distinct, non-blank paths before constructing the `MediaItem` used by `PortraitPlayerScreen`.
- The split audio path is carried as the local `MediaItem` tag by `LocalPlaybackPolicy.mediaItemFor(...)`, preserving the `MediaController.setMediaItem(...)` production path while avoiding remote URI reconstruction in Compose.
- `PlaybackSessionService` now builds the canonical service-owned `ExoPlayer` with `SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this))`.
- `SplitAudioMediaSourceFactory.createMediaSource(...)` builds the canonical video source from the controller-provided `MediaItem`, reconstructs the optional local audio source with `LocalPlaybackPolicy.mediaItemFor(audioPath)`, and returns `MergingMediaSource(videoSource, audioSource)` when split audio exists.
- Single-file completed offline items still use the delegate video source unchanged.

## Deterministic qualification

`LocalPlaybackPolicyTest` proves split video/audio plans retain distinct local paths, reject remote audio assets, reject identical audio/video paths, and keep all playback requests local. `SplitAudioPlaybackSessionIntegrationTest` guards that the service-owned canonical player installs the split-audio source factory, delegates DRM/load-error policies, and carries the split audio path into that production session factory.

## Exact-head evidence

Exact master `3a4d92a249405754a0c678bae7d60d1ba155b48c` passed the required six-workflow matrix: CI `36652907080`, Android smoke `36652907084`, Android FGS timeout `36652907127`, Supply chain `36652907103`, CI evidence `36652907102`, and Deterministic E2E fixture `36652907106`.

This evidence satisfies the RMD-903 implementation and qualification requirements. The canonical TODO can be reconciled by checking the RMD-903 subtasks and referencing this document plus the exact-head matrix above.
