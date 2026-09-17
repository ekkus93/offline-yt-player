# Adaptive Local Playback Qualification

## Scope

This note qualifies the OYP-802 playback boundary for persisted adaptive assets after the Media3 integration added in PR #113.

OYP-802 requires the application to:

- represent separate local audio/video streams;
- prove Media3 playback of coordinated local assets where supported;
- persist the asset relationship in the library database.

The current implementation now has both the production Media3 construction path and a deterministic JVM-testable planning seam.

## Implementation evidence

`LocalPlaybackAsset` carries a required local video path and an optional local audio path. `LocalPlaybackPolicy.mediaSourcePlanFor` validates that the paths are local, non-blank, and distinct when an adaptive audio asset is present. That plan feeds `LocalPlaybackPolicy.mediaSourceFor`, which builds one Media3 source for single-file assets or a `MergingMediaSource` from separate video and audio `MediaSource` instances for adaptive assets.

The library persistence model already stores media asset relationships separately from the player surface. OYP-802 therefore has a stable path from persisted asset identity to player construction without reintroducing remote playback URIs.

## Automated qualification

`LocalPlaybackPolicyTest` covers the deterministic policy surface that protects coordinated local adaptive playback:

- adaptive assets preserve distinct local video and audio paths;
- single-file assets do not falsely claim separate adaptive playback;
- remote audio URIs are rejected before Media3 construction;
- identical video/audio paths are rejected before Media3 construction.

This complements the production Media3 `MergingMediaSource` construction path added in PR #113. The test intentionally validates the pure policy seam rather than depending on a device-only player session in local JVM tests.

## Remaining boundary

This closes the deterministic implementation/qualification boundary for OYP-802. Later OYP-900 and OYP-1900 work must still prove actual offline player behavior through Android playback and end-to-end fixture flows.
