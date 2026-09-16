# v1 media compatibility and muxing policy

This document makes the OYP-801/OYP-803 release policy concrete. It complements ADR-004; it does not claim the still-open device/Media3 qualification required by OYP-802.

## Preferred Android playback formats

For v1, source adapters should rank directly playable choices in this order when equivalent qualities are available:

1. MP4 containing H.264/AVC video plus AAC-LC audio.
2. MP4 containing H.264/AVC video-only plus a separate M4A/MP4 AAC-LC audio asset when the combined choice is unavailable or materially worse.
3. WebM/VP9 plus Opus only after Media3/device qualification demonstrates support on the declared Android device profile.
4. Other codecs/containers are not preferred merely because an extractor exposes them; they require explicit compatibility evidence before becoming a v1 default.

A source adapter must retain normalized codec, container, stream-role, resolution, bitrate, and direct-play compatibility metadata in the portable domain model. User-facing quality choices should prefer `Compatibility::Preferred`, then `Compatibility::Compatible`, and should not silently select `RequiresMuxing` or `Unsupported` choices.

The policy intentionally prefers a somewhat lower-quality directly playable asset over a nominally higher-quality choice that would require an unqualified native mux/transcode dependency. The UI may expose a separately qualified adaptive choice when its local playback plan is proven.

## Separate adaptive assets

The portable model already supports `StreamRole::VideoOnly` and `StreamRole::AudioOnly`, and a `DownloadPlan`/`LibraryItem` may contain multiple local assets. Persistence records asset relationships under the owning library item rather than embedding Android URIs.

That representation is necessary but not sufficient to close OYP-802. OYP-802 remains open until automated or device-backed Media3 qualification proves coordinated local playback of a representative separate video/audio pair and the Android playback path consumes that persisted relationship.

## Muxing decision gate

FFmpeg or another native muxer is **not** a baseline v1 dependency. A muxer may be proposed only when all of the following are true:

- a supported v1 source/quality case cannot be played correctly as either a directly playable combined asset or a qualified separate local video/audio plan;
- the failure is reproduced by a deterministic fixture and, where platform behavior matters, the supported Android qualification profile;
- the proposed muxer resolves that demonstrated case without requiring network access during completed playback;
- binary-size and ABI impact are measured;
- license obligations and generated notices are reviewed;
- maintenance, update, CVE, and supply-chain ownership are documented;
- the dependency/license audit and ADR-004 are updated in the same qualified change.

Until those conditions are met, choices requiring muxing remain non-default and must not cause a muxer to be pulled into the release artifact.

## Release qualification boundary

OYP-801's compatibility policy and OYP-803's decision gate are engineering policy. They do not substitute for OYP-802's Media3 proof, OYP-900's offline playback qualification, or the final device/portrait closeout. Those gates remain open until their executable evidence exists.
