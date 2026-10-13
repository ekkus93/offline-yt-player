# RMD-1802 — Playback metadata/track label disclosure review (2026-10-13)

## Finding

After source/download metadata was persisted, `LocalPlaybackPolicy.mediaItemFor` copied the stored title into Media3 `MediaMetadata` and `subtitleConfigurationFor` copied stored subtitle labels into `SubtitleConfiguration`. `availableSubtitleLabels` and `availableAudioLabels` also returned raw labels for player controls. Because provider-generated metadata could include signed URLs or credential-bearing diagnostics, these presentation routes bypassed the already shared redaction policy. The underlying media paths and format/track identities are distinct from user-visible labels and must remain unchanged.

## Correction

Use `SourceMetadataPolicy.diagnostic` followed by field-appropriate maximum length to present the MediaSession title, subtitle configuration label, and player subtitle/audio labels. Keep local media URIs, MediaSession track selection, playback position and durable metadata unmodified.

## Qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt::mediaSessionAndTrackPresentationRedactProviderControlledLabels` exercises real Media3 `MediaItem` creation and audio/subtitle UI-label projection with synthetic signed URLs and asserts redaction and unchanged local track behavior. Host-JVM proof moves string presentation off the emulator; existing Android instrumentation qualifies MediaSession and actual playback behaviors.

Do not claim the finding qualified until the exact implementation head passes all standard CI workflows, including Android smoke, FGS timeout/UIDT, process-death/cold-start, and real network E2E. Independent final review, closeout and external release-policy/legal gate remain open.

## Qualification failure and tier correction (2026-10-13)

First implementation head `73b557d39c9711806961594ad4d710af2cfad92a` failed main CI run [38373149050](https://github.com/ekkus93/offline-yt-player/actions/runs/38373149050) specifically in Android host unit test `LocalPlaybackPolicyTest.mediaSessionAndTrackPresentationRedactProviderControlledLabels`: a `RuntimeException` at line 114 occurred when the test invoked Android `Uri` / Media3 item construction under ordinary host JVM where framework methods are unavailable. Rust, Kotlin production compilation, and UniFFI/ABI succeeded; this is an incorrect qualification tier, not evidence that the sanitization behavior failed.

The host unit test now checks the pure production media-title projection and audio/subtitle label mapping; `PlaybackSessionBehaviorInstrumentedTest.media3ItemSanitizesProviderTitleAndSubtitleLabelsBeforeSessionExposure` calls the **real** `LocalPlaybackPolicy.mediaItemFor` on an Android emulator and asserts both MediaMetadata title and SubtitleConfiguration labels are redacted. This preserves tiered qualification: host string mapping and device Media3 runtime. Re-run all eight exact-head workflows on the corrected SHA and do not check closeout before their completion.
