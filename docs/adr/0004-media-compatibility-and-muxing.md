# ADR 0004: Android media compatibility and muxing

Status: accepted for v1

## Context

Source adapters can expose combined streams or adaptive video-only/audio-only streams in several containers and codecs. Offline playback should prefer assets Android Media3 can consume directly, while avoiding a large native muxing dependency until tests demonstrate that one is necessary.

## Compatibility policy

Quality curation ranks directly playable combined MP4/H.264/AAC as the preferred baseline when available. Compatible adaptive assets may be retained separately: MP4/H.264 video with M4A/AAC audio and WebM/VP9 video with WebM/Opus audio are eligible for coordinated local playback when the Media3 qualification suite proves the pair on supported Android profiles.

A higher nominal resolution does not outrank a lower directly playable choice merely because it is larger. The UI exposes curated choices and compatibility state rather than raw provider format identifiers.

Provider metadata is normalized in Rust. Persisted library records describe relative local assets and their relationship; they must not depend on expiring remote URLs or Android-only content URIs.

## Adaptive asset strategy

A download plan may contain separate video and audio assets for one library item. Both assets must be fully verified before the item is promoted to completed state. The library item remains incomplete if either required asset is absent or corrupt.

Android Media3 owns local playback composition. The player must use local URIs only for completed offline items and must not silently fetch a missing adaptive component from the network.

## Muxing decision gate

Do **not** add FFmpeg or another general-purpose muxer to v1 merely to simplify playback code. A muxer becomes eligible only if automated/device qualification demonstrates a supported quality choice that cannot be played reliably as either a directly playable combined asset or a coordinated local adaptive pair.

Before adding a muxer, record:

1. the failing codec/container/asset combination and reproducible Media3 evidence;
2. why format-selection fallback cannot provide an acceptable directly playable choice;
3. binary-size and startup/storage impact;
4. Android ABI maintenance cost;
5. license and redistribution obligations for the exact build configuration;
6. security/update implications of the native dependency.

The preferred remediation order is: choose another compatible source format, play separate compatible local assets, add a narrowly scoped remux implementation, and only then consider a general FFmpeg-class dependency.

## Licensing

Any muxer addition requires an explicit dependency/license review before merge. In particular, build-time feature selection can change redistribution obligations, so the review must cover the actual compiled configuration rather than the project name alone.

## Consequences

The portable core can represent both combined and separate assets without committing the application to a muxer. Media3 compatibility tests, not source-provider assumptions, decide which curated choices are offered as preferred. Public release remains subject to the separate source-service policy gate.
