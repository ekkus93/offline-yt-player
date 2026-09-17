# Offline Playback Qualification

## Scope

OYP-905 verifies the deterministic offline invariants that can be enforced in repository CI without a physical Android device.

## Fixture and network isolation

The qualification uses a completed local-file playback fixture. `LocalPlaybackPolicy` rejects HTTP/HTTPS playback URIs and constructs Media3 items from `Uri.fromFile`, so a completed playback plan cannot silently fall back to networking.

## Cold start and transport

A persisted start position is carried into a newly constructed `LocalPlaybackAsset`; `LocalPlayerController` applies that position with `seekTo` before preparation. Media3 owns play/pause/resume and seeking once the local source is prepared. The deterministic tests also lock the skip interval and near-end completion behavior.

## Qualification boundary

`OfflinePlaybackQualificationTest` proves the local-only source plan, restored-position cold-start path, and Media3 preparation/seek wiring in every Android unit CI run. Physical-device behavior and process-kill/network-toggle validation remain part of OYP-1901 end-to-end qualification; OYP-905 does not claim that host JVM tests emulate device media decoders.
