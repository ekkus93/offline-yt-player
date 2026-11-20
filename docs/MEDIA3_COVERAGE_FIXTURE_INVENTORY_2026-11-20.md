# Offline Media3 deterministic playback fixture inventory — 2026-11-20

This inventory supports `docs/TEST_COVERAGE_EXPANSION_TODO_2026-11-19.md` COV-601 and later lifecycle/corruption cases. The fixture is entirely local. No live YouTube, external URL, or network service is used.

## Bundled genuine media

`app/src/androidTest/assets/playback/combined_0p5s.mp4.b64` is a Base64-encoded, 3,064-byte MP4 container with a 32×32 H.264 video track and 0.5-second AAC mono audio track at 16 kHz. SHA-256 of decoded MP4: `8d2af7ef700d6b040178d4d76967423c3d47860f00a58ff5508ba6c3ce070a2c`. It was generated using FFmpeg 6.1.7 from lavfi `color=black:s=32x32:r=2:d=0.5` and `sine=frequency=440:sample_rate=16000:duration=0.5`, H.264 CRF 40, AAC 12k, with fast-start MP4 flags.

`PlaybackSessionBehaviorInstrumentedTest::packagedOfflineAvFixtureCanBeDemuxedIntoRealVideoAudioAndSubtitles` decodes the fixture from the **test APK assets** and checks the digest, uses real Android `MediaExtractor` and `MediaMuxer` to derive video-only MP4 and audio-only M4A tracks, and creates a bounded local WebVTT subtitle file. MIME and local `file:` identifiers are required by assertions. The derived files are removed on test completion.

## Existing relevant behavioral tests

- `PlaybackSessionBehaviorInstrumentedTest` already tests two MediaControllers sharing the real service-owned player, seek/pause/play, media keys, speed, audio usage/noisy handling, text/audio language choices, saved-position restoration and teardown ordering, often using a locally generated 45-second WAV.
- `Rmd1502SplitAvOfflineInstrumentedTest` supplies an independent embedded video-only MP4 and generated WAV through a localhost transfer fixture and checks end-to-end split A/V offline playback and cold-start persistence.
- `Rmd1503SubtitleOfflineInstrumentedTest` supplies WAV and WebVTT fixtures, asserting persisted subtitle identity, offline local playback and cold-start restoration.
- `Rmd1501AndroidRuntimeFixtureInstrumentedTest` provides deterministic complete audio path tests with native download queue and local WAV.

Future COV-602–606 should reuse these assets and test production Media3 lifecycle/error paths rather than duplicate already tested UI labels. Emulator audio routing is not identical to Samsung A54 physical output or focus-policy behavior. This inventory does not qualify physical-device playback.
