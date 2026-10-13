# RMD-1802 — Playback metadata/track label disclosure review (2026-10-13)

## Finding

After source/download metadata was persisted, `LocalPlaybackPolicy.mediaItemFor` copied the stored title into Media3 `MediaMetadata` and `subtitleConfigurationFor` copied stored subtitle labels into `SubtitleConfiguration`. `availableSubtitleLabels` and `availableAudioLabels` also returned raw labels for player controls. Because provider-generated metadata could include signed URLs or credential-bearing diagnostics, these presentation routes bypassed the already shared redaction policy. The underlying media paths and format/track identities are distinct from user-visible labels and must remain unchanged.

## Correction

Use `SourceMetadataPolicy.diagnostic` followed by field-appropriate maximum length to present the MediaSession title, subtitle configuration label, and player subtitle/audio labels. Keep local media URIs, MediaSession track selection, playback position and durable metadata unmodified.

## Qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt::mediaSessionAndTrackPresentationRedactProviderControlledLabels` exercises real Media3 `MediaItem` creation and audio/subtitle UI-label projection with synthetic signed URLs and asserts redaction and unchanged local track behavior. Host-JVM proof moves string presentation off the emulator; existing Android instrumentation qualifies MediaSession and actual playback behaviors.

Do not claim the finding qualified until the exact implementation head passes all standard CI workflows, including Android smoke, FGS timeout/UIDT, process-death/cold-start, and real network E2E. Independent final review, closeout and external release-policy/legal gate remain open.
