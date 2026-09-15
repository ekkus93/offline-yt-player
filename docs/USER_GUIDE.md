# Offline YT Player — User Guide

Offline YT Player is an Android-first, portrait-only application for saving supported media to a local library and playing completed items without a network connection. v1 is still under engineering qualification and is not yet a public-release build.

## Offline workflow

1. Open **Add**, paste a supported `http` or `https` media URL, and choose **Analyze**. Android Share can also send plain-text URLs into the app.
2. Review the bounded Download Setup preview. Choose a curated quality; advanced subtitle/audio choices live on the separate Options page so primary actions remain visible.
3. Choose **Download**. The durable download engine is designed to preserve enough state to pause, retry, and resume supported ranged transfers after interruption.
4. Wait until the item is verified and promoted into the completed Library. Incomplete/temporary assets are not treated as playable completed items.
5. Open **Library** and choose **Play**. Completed items route through the local-playback policy; remote HTTP/HTTPS playback URIs are rejected at that boundary.
6. Playback uses the portrait player and MediaSession controls. The app does not require a network request merely to play a completed local item.

Do not delete or externally modify application-owned media/database files while the app is running. If an asset is missing or corrupt, the app should treat that as a recoverable/error state rather than silently claiming the item is healthy.

## Download and storage behavior

Downloads use temporary/incomplete storage until verification succeeds. When the server supports ranges, interrupted transfers can resume from verified partial state; otherwise the safe behavior is to restart rather than append incompatible bytes. Retry is bounded rather than infinite.

The app may fail a download before transfer when free-space preflight indicates insufficient storage. Cancellation and cleanup must not promote partial content into the Library. Completed media, thumbnails, subtitle assets, source identity, and playback metadata are represented as local library state so offline reconstruction does not depend on the provider being reachable.

The Settings surfaces expose download, playback, storage, appearance, and About categories. Storage cleanup should be used for application-owned cache/orphan/incomplete data; deleting arbitrary files outside the app is not part of this workflow.

## Privacy and diagnostics

Media URLs and provider responses are untrusted input. The application validates shared URLs and sanitizes remote metadata/path components before use. Provider adapters are replaceable and must not expose cookies, tokens, sensitive headers, or signed URLs through generic UI/domain diagnostics.

Offline YT Player is intended to keep completed media local to the device. The app should not upload a completed library merely to support playback. Network access is used for explicit resolution/download operations and provider interactions required by those operations.

Diagnostic information should identify actionable categories such as unsupported source, network failure, extractor/source incompatibility, storage pressure, integrity failure, or missing local assets without disclosing credentials or signed request material.

## Troubleshooting

### A shared URL is rejected

Only bounded plain-text Android Share input containing a valid HTTP/HTTPS URL is accepted. Malformed input, unsupported MIME/action combinations, oversized share text, and non-web schemes are rejected. If the URL is syntactically valid but the provider is unsupported, use a supported source instead.

### Analysis or download fails

Check connectivity and the Downloads error state. A network error may be retryable; an unsupported source or extractor/source incompatibility is not fixed by infinite retry. If storage is low, free space and retry. Wi-Fi-only policy, when enabled, must not be silently bypassed.

### A download was interrupted

Use Resume when the persisted job is resumable. The engine validates continuation state and server range behavior. If safe continuation is unavailable, restarting the transfer is preferable to producing a corrupt completed item.

### A completed item will not play

Completed playback is intentionally local-only. Confirm the application's local media asset still exists and has not been moved/deleted externally. A missing/corrupt local asset should be surfaced explicitly. Re-downloading from the source may be required if the local copy is no longer valid.

### Playback stops when headphones disconnect

This is expected protective behavior: the MediaSession player handles Android's audio-becoming-noisy signal to avoid unexpectedly switching playback to speakers.

### Controls do not rotate to landscape

This is intentional. v1 supports portrait mode only; there is no landscape/rotate playback action. Primary controls are designed to remain in fixed or bounded portrait regions.

## Current release limitations

Public/app-store distribution remains blocked pending source-service terms/policy/legal review. Live provider extraction can change independently of the app and is intentionally isolated behind the `MediaSource` adapter boundary. Device/emulator-only acceptance cases must be explicitly qualified before v1 engineering closeout.

For developer/build troubleshooting, see `README.md`, `docs/OFFLINE_YT_PLAYER_SPEC.md`, and `docs/OFFLINE_YT_PLAYER_TODO.md`.
