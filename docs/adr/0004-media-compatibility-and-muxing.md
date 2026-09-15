# ADR 0004: Media compatibility, adaptive assets, and muxing

Status: accepted for v1

## Context

YouTube and future sources may expose combined audio/video streams or separate adaptive streams. Offline YT Player must prefer reliable Android Media3 playback without automatically accepting a large native muxing dependency.

## Direct-play compatibility policy

The portable core classifies normalized formats; Android Media3 remains the playback authority for the actual device. For v1, preferred choices are directly playable combinations with broad Android support:

- MP4 containing H.264/AVC video plus AAC audio;
- MP4 H.264 video-only paired with M4A/MP4 AAC audio;
- WebM VP9 video-only paired with WebM Opus audio where the target device reports support.

A combined MP4 H.264/AAC stream is preferred over an adaptive alternative when the quality difference is modest because it minimizes failure modes, storage bookkeeping, and playback coordination. Curated quality choices must expose compatibility rather than provider-specific format IDs to the UI.

## Separate adaptive assets

Separate video and audio are first-class local assets, not temporary implementation details. A download plan may contain coordinated video and audio assets for one library item. Persistence records their common item identity and distinct asset roles. Media3 playback should construct one local playback presentation from those assets when the platform path is qualified; no network URL is required after completion.

Until coordinated local adaptive playback is proven in Android instrumentation/E2E qualification, such choices remain `RequiresSeparateAssets` rather than `Preferred`.

## When muxing is required

Muxing is considered required only when a desired quality cannot be played reliably as either a directly playable combined file or a qualified coordinated local audio/video pair. Examples include incompatible container/codec combinations, a device/API limitation preventing coordinated local playback, or a release requirement for a single-file artifact that cannot otherwise be met.

Transcoding is out of scope for the initial decision and must not be silently substituted for muxing.

## Muxer decision

Do **not** add FFmpeg or another muxer to v1 yet. The current normalized model can represent combined and separate assets, and there is not yet test evidence demonstrating that a muxer is necessary for the supported compatibility envelope.

If qualification later proves muxing necessary, evaluate alternatives before adding a dependency. The evaluation must record:

1. APK/AAB size impact per supported ABI;
2. native-code maintenance and Android API/ABI support;
3. license of the exact build/configuration and redistribution obligations (including whether enabled codecs alter those obligations);
4. security/update surface and reproducible-build implications;
5. whether a smaller container-specific native or pure-Rust implementation satisfies the tested need.

FFmpeg is therefore an evaluated architectural option, not an approved dependency. No muxer may be added merely to simplify implementation.

## Consequences

- `Compatibility::Preferred` is reserved for directly playable choices with the supported codec/container policy.
- `Compatibility::RequiresSeparateAssets` signals a coordinated local playback path that still requires Media3 qualification.
- `Compatibility::RequiresMuxing` is retained so unsupported raw formats are not falsely advertised as directly playable.
- The database/library contract must preserve relationships among assets belonging to one item.
- Adding a muxer requires a follow-up ADR with measured compatibility evidence and license/size analysis.
