# ADR 0004: Android media compatibility and muxing gate

Status: accepted for v1

## Context

Offline YT Player may receive combined streams or adaptive video/audio streams. The portable core must describe those assets without assuming that every source can provide a single directly playable file. Android playback is implemented with Media3, while any future muxing implementation would materially affect binary size, licensing, maintenance, and the attack surface exposed to untrusted media.

## Decision

The resolver ranks directly playable Android formats ahead of formats that require transformation when the quality tradeoff is reasonable. For v1, the preferred compatibility set is:

- MP4 with H.264/AVC video and AAC audio for combined assets.
- MP4 with H.264/AVC for video-only adaptive assets.
- M4A/MP4 with AAC for audio-only adaptive assets.
- WebM VP9 video and WebM Opus audio may be retained as separate directly playable assets when Media3/device capability permits them.

The portable domain model continues to represent stream role and compatibility explicitly. Separate audio/video files remain separate local assets linked to one library item; Android Media3 is responsible for coordinated local playback when that combination is supported. Persisted records use relative paths and media metadata rather than Android-only URI types.

## Muxing gate

A muxer is required only when qualification demonstrates that a desired quality cannot be played reliably as either a compatible combined asset or coordinated local adaptive assets. We will not add FFmpeg or another native muxing stack speculatively.

Before introducing a muxer, the change must record:

1. A reproducible Media3/device compatibility failure that cannot be solved by selecting another acceptable source format.
2. APK/AAB size impact for every supported Android ABI.
3. License inventory and redistribution obligations for the exact build configuration, including codec/library licensing implications.
4. Security and update implications of parsing untrusted media in the added native dependency.
5. Deterministic tests proving the muxed output is playable offline and that incomplete output is never promoted into the library.

FFmpeg is an evaluation candidate, not an approved dependency. Smaller purpose-built/native alternatives may be evaluated against the same gate. No muxer dependency is approved until the gate above is satisfied.

## Consequences

Quality-choice curation should prefer `Compatibility::Preferred`, then `Compatibility::RequiresSeparateAssets`, and expose `Compatibility::RequiresMuxing` only when the application can actually satisfy the muxing requirement. Download planning must preserve the relationship between adaptive assets so the library and player can reconstruct one logical item without provider-specific types leaking into the UI.

This decision keeps the first Android release smaller and easier to audit while preserving a documented path to muxing if measured playback compatibility requires it.
