# RMD-903 split A/V playback reconciliation — 2026-09-29

RMD-903 requires completed offline playback to use both local video and local audio assets when an item has separate streams, build the correct merged Media3 source, keep playback URIs local, and prove the split A/V path deterministically.

## Current production path

`app/src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt` satisfies the split A/V source-construction requirements:

- `LocalPlaybackAsset` carries `videoPath` plus optional `audioPath` for completed local items that require separate A/V playback.
- `LocalPlaybackPolicy.validate` rejects blank paths, remote `http://` or `https://` playback URIs, and identical video/audio paths for separate-asset playback.
- `LocalPlaybackPolicy.mediaSourcePlanFor` preserves the distinct local video path, audio path, subtitle tracks, audio tracks, and restored start position.
- `LocalPlaybackPolicy.mediaSourceFor` builds a local video MediaSource from the validated asset, builds a separate local audio MediaSource when `audioPath` is present, and returns `MergingMediaSource(videoSource, audioSource)` for split A/V playback.
- `LocalPlaybackPolicy.mediaItemBuilderFor` uses `Uri.fromFile(File(path))`, preserving the offline/local playback boundary for completed library assets.

## Deterministic qualification

Existing deterministic coverage in `LocalPlaybackPolicyTest` proves separate local video/audio paths are preserved, single-file items do not claim split A/V playback, remote audio paths are rejected, and identical video/audio paths are rejected.

`app/src/test/java/com/ekkus/offlineytplayer/playback/SplitAvPlaybackSourcePolicyTest.kt` adds a focused production-source guard for the merged Media3 source construction and local-only URI boundary.

## Boundary

This closes the deterministic source-construction proof for RMD-903. Full RMD-1502 end-to-end split A/V offline playback remains separate because it must prove the Android app can schedule/download separate fixture assets, cold-start offline, and play synchronized merged A/V through the canonical session.
