# Local Player Integration Audit

## Scope

This note covers the first OYP-901 integration step: constructing a Media3/ExoPlayer instance exclusively from already-local playback assets.

It does not claim the later OYP-902 portrait player screen, OYP-903 lifecycle persistence, OYP-904 MediaSession integration, or OYP-905 offline playback end-to-end qualification.

## Implementation

`LocalPlayerController` owns the Android player construction boundary. It accepts a `LocalPlaybackAsset`, validates it through `LocalPlaybackPolicy`, constructs a Media3 `MediaSource` from local file-backed media items, seeks to a persisted start position when present, and prepares the player.

The implementation inherits the OYP-802 asset rules:

- remote `http://` and `https://` playback URIs are rejected before player construction;
- single-file assets use a single local Media3 source;
- separate local audio/video assets use the `MergingMediaSource` construction path;
- adaptive audio and video assets must remain distinct local paths.

## Qualification

`LocalPlaybackPolicyTest` now covers the deterministic player-planning seam used by `LocalPlayerController`, including preservation of the persisted start position. Full device/player behavior remains in OYP-905 and OYP-1900.
