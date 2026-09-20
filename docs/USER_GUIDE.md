# Offline YT Player — User Guide

Offline YT Player is an Android-first, portrait-only application for saving supported media to a local library and playing completed items without a network connection. v1 is still under engineering remediation and is not yet a public-release build. This guide describes the intended operational UI where the corresponding runtime path is available; it does not override unchecked acceptance gates in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Add and download workflow

1. Open **Add**, enter a supported `http` or `https` media URL, and choose **Analyze**. Android Share can also route bounded plain-text web URLs into the app.
2. After successful resolution, review **Download Setup**. Choose a curated quality. Additional subtitle/audio choices belong on the separate Options surface rather than hiding the primary action behind horizontal scrolling.
3. Choose **Download** to enqueue the selected work. The durable queue is the source of truth for download state; a UI transition by itself does not mean media has been downloaded.
4. Follow progress in **Downloads**. Where the current job state permits it, pause/resume/cancel/retry controls act on durable queue state rather than fabricated UI-only state.
5. Wait until verification and promotion complete. Incomplete or temporary assets are not playable Library items.
6. Open **Library** and choose **Play** for a completed item. The local-playback boundary rejects remote HTTP/HTTPS playback URIs.

The complete real-provider Add/Share → resolve → download → Library → offline-playback path remains subject to the remediation checklist's exact-head and Android E2E qualification. A control being visible does not by itself prove that its entire downstream workflow has reached engineering closeout.

## Offline playback guarantees and limitations

A successfully completed and promoted Library item is designed to play from application-owned local media. Playback of that local item must not require a provider/network request merely to start or continue playback. Local playback is MediaSession-owned so in-app and system controls operate on the same player instance.

Offline availability depends on the required local assets still existing and remaining valid. Deleting or externally modifying application-owned media/database files can make an item unavailable. Provider re-resolution is not an offline fallback. Split audio/video, subtitles, thumbnails, playback-position restore, and other asset-specific guarantees remain governed by their individual remediation acceptance gates until exact-head qualification is recorded.

The UI is portrait-only. There is no landscape/rotate playback action. Primary actions are intended to remain visible in fixed or bounded portrait regions; naturally unbounded collections such as Library and Downloads may scroll within their list regions.

## Download and storage behavior

Downloads use temporary/incomplete storage until verification succeeds. When the server and persisted state support safe ranged continuation, interrupted transfers can resume; otherwise restarting is safer than appending incompatible bytes. Retry is bounded rather than infinite.

A download may fail before transfer when free-space preflight indicates insufficient storage. Cancellation and cleanup must not promote partial content into the Library. Completed media and associated local metadata/assets are intended to be represented by durable library state so offline reconstruction does not depend on the provider being reachable.

Do not delete or externally modify application-owned media/database files while the app is running. Storage cleanup is for application-owned cache, orphan, or incomplete data; deleting arbitrary files outside the app is not part of this workflow.

## Recovery and error states

### Shared URL rejected

Only bounded plain-text Android Share input containing a valid HTTP/HTTPS URL is accepted. Malformed input, unsupported MIME/action combinations, oversized share text, and non-web schemes are rejected. A syntactically valid URL can still fail because its provider or URL form is unsupported.

### Resolution fails

Resolution can fail because of connectivity, unsupported input, unavailable/private media, or provider/extractor changes. Provider/source incompatibility is distinct from a transient network error and must not be hidden behind infinite generic retry.

### Download waits or fails

Check the durable Downloads state and its diagnostic category. Wi-Fi-only policy must not be silently bypassed. Storage pressure can prevent a transfer from starting. A transient network failure may be retryable; a nonretryable provider/source error should remain nonretryable.

### Download interrupted or process restarted

Use **Resume** when the persisted job is resumable. Safe continuation depends on persisted progress and compatible server range behavior. If continuation cannot be proven safe, restarting is preferable to producing corrupt media. Process-death/reboot reconciliation is an explicit remediation gate; do not assume every interrupted state is recoverable until that qualification is complete.

### Notification permission denied

On Android versions that require notification permission, denial must not silently mark queued work complete or erase durable queue state. The in-app Downloads state remains authoritative. Android background execution differs by API level; see `docs/ANDROID_BACKGROUND_EXECUTION.md` for the engineering model and its remaining limits.

### Completed item will not play

Completed playback is local-only. If an application-owned media asset is missing or corrupt, the app should surface an explicit missing/integrity state rather than silently claiming the item is healthy. Re-downloading may be required if the local copy can no longer be repaired.

### Playback stops when headphones disconnect

This is expected protective behavior: the MediaSession player handles Android's audio-becoming-noisy signal to avoid unexpectedly switching playback to speakers.

## Privacy and diagnostics

Media URLs and provider responses are untrusted input. Shared URLs are validated and remote metadata/path components must be sanitized before use. Provider adapters are replaceable and must not expose cookies, tokens, sensitive headers, or signed URLs through generic UI/domain diagnostics.

Offline YT Player is intended to keep completed media local to the device. Network access is for explicit resolution/download operations and provider interactions required by those operations; playback of a valid completed local item does not require uploading the library or fetching the provider again.

Diagnostics should identify actionable categories such as unsupported source, network failure, extractor/source incompatibility, storage pressure, integrity failure, or missing local assets without disclosing credentials or signed request material.

## Current release limitations

Public/app-store distribution remains blocked pending source-service terms/policy/legal review. That approval is external to engineering and is not granted by passing CI or completing this guide. Live provider extraction can change independently of the app and is intentionally isolated behind the `MediaSource` adapter boundary. Device/emulator instrumentation, deterministic fixture E2E, and other unchecked remediation acceptance cases must be explicitly qualified before v1 engineering closeout.

For developer/build troubleshooting, see `README.md`, `docs/ANDROID_RUST_FFI_BUILD.md`, `docs/ANDROID_BACKGROUND_EXECUTION.md`, `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`, and `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
