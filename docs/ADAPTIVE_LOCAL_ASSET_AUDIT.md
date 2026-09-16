# Adaptive local asset audit

This document qualifies the implementation boundary for `docs/OFFLINE_YT_PLAYER_TODO.md` OYP-802.

## Representation

The portable domain already represents a download plan as one or more `DownloadPlanAsset` records. Each asset carries a stable asset ID, media kind, relative path, expected size/integrity metadata, and MIME type. A plan can therefore retain separate local video and audio assets without provider-specific types leaking into persistence or UI code.

`LibraryStore` persists every completed `LocalAsset` as an independent row keyed by `(item_id, asset_id)`, so the relationship between the completed library item and its coordinated assets survives process death and database reconstruction.

## Media3 playback

`LocalPlaybackAsset` has a required local `videoPath` and an optional local `audioPath`. `PortraitPlayerScreen` now builds local `ProgressiveMediaSource` instances through Media3's `DefaultDataSource` and, when `audioPath` is present, combines the video and audio sources with `MergingMediaSource` before preparing ExoPlayer. The same player path continues to handle a single combined local file when no separate audio path exists.

`LocalPlaybackPolicy.validate` rejects HTTP/HTTPS paths for both video and optional audio, preserving the completed-library offline contract.

## Qualification boundary

Repository Android lint/unit/build CI provides compile-time qualification that the pinned Media3 version supports the coordinated local-source construction. Deterministic device/emulator playback with representative encoded fixture assets remains part of OYP-905 and OYP-2304; this audit does not claim that later offline acceptance gate is complete.

OYP-802's three implementation subtasks are satisfied: separate assets are represented, Media3 is wired to coordinate local audio/video sources, and persistence retains the asset relationship. Final device-level playback acceptance remains separately tracked rather than being conflated with the implementation task.
