# Offline YT Player — User Guide

Offline YT Player is designed for preparing media before travel or other periods with unreliable connectivity. The Android app is portrait-only and keeps primary actions visible rather than hiding them behind gestures or horizontally scrolling controls.

> The application is still under v1 engineering. Provider availability and public distribution are not guaranteed until the source-service policy/legal release gate is complete.

## Offline workflow

### Add a video

1. Open **Add** from the bottom navigation.
2. Paste a supported `http` or `https` video URL, or share a URL to Offline YT Player from another Android app.
3. Analyze the URL. A valid share may open the Download Setup preview directly instead of showing an empty Add screen.
4. Review the title, duration, estimated size when available, and curated quality choices.
5. Use **Options** for secondary audio/subtitle choices rather than crowding the primary setup screen.
6. Start the download.

### Track a download

Open **Downloads**. Transfer state is explicit: active, paused, failed, retrying, or completed. Important actions such as Pause, Resume, Retry, and Cancel are visible controls rather than swipe-only gestures.

The download engine uses bounded retries. When a server supports byte ranges, a valid partial transfer can resume instead of restarting. If continuation data cannot be trusted or the server cannot safely resume, the downloader falls back rather than appending incompatible bytes.

### Play offline

After a download is verified and promoted into the completed library:

1. Open **Library**.
2. Select **Play** on the completed item.
3. Playback is constructed from local media paths. Completed playback is not allowed to substitute an HTTP/HTTPS media URI.
4. Use the fixed portrait transport controls to play/pause, seek, skip, and adjust supported playback options.

Keep all media files managed by the application. Deleting or externally moving a completed media asset can make the corresponding library item unavailable until it is reconciled or removed.

## Download and storage behavior

Downloads are staged separately from completed library items. A partially downloaded item is not presented as completed media. The core verifies completion and then promotes the completed asset into its library location.

Durable download state is designed to survive process interruption. Startup reconciliation can distinguish retained partial work from completed library assets. Storage-space checks can fail a transfer before completion when there is not enough room rather than falsely marking it successful.

Cancellation and cleanup may remove temporary/partial data according to the download state. Completed library media remains local until the user removes it from the device.

The Storage settings surface summarizes the storage location and provides cleanup-oriented controls as those capabilities are qualified. Avoid manually editing the application's database or partial-download directory.

## Privacy and network behavior

Offline YT Player is architected so that completed playback uses local assets and does not require a network request. Network access is still required while resolving remote metadata or downloading remote media.

Shared text and URLs are treated as untrusted input. The Android share entry point accepts bounded plain text and validates HTTP/HTTPS URL structure before routing it into setup.

Provider/source implementations must not expose cookies, authentication tokens, sensitive headers, or signed URLs through diagnostics. Provider-specific extraction logic is isolated behind a generic source adapter so it can be replaced without coupling credentials or provider internals to the library UI.

Do not assume the application provides anonymity. Your network provider and the remote media service can still observe ordinary network traffic made while resolving or downloading content.

## Troubleshooting

### A shared URL does not open setup

Confirm that the sending application shared plain text containing a complete `http://` or `https://` URL. Malformed URLs, unsupported MIME types, non-network schemes, and excessively large shared text are rejected intentionally.

### A URL is reported as unsupported

The generic source registry only routes URLs to adapters that explicitly recognize them. Provider support can change independently of the downloader. An unsupported-source or extractor-incompatibility error is different from a generic network failure.

### A download pauses, retries, or fails

Check connectivity and available storage. Retry is bounded; permanent failures are not retried forever. If a remote server changes or invalidates a partial response, safe resume may restart rather than reuse incompatible partial bytes.

### A completed item will not play

Completed playback requires valid local assets. If media was manually deleted, moved, or corrupted outside the application, the local-only playback guard will not fetch a remote replacement. Remove/re-download the item after checking storage.

### Playback stops when audio routing changes

The MediaSession participates in Android audio focus and handles the audio-becoming-noisy signal so playback can respond safely when headphones or another audio route disconnects.

### The UI does not rotate

This is intentional. Android v1 supports portrait orientation only. The player uses a 16:9 video region inside the portrait layout; there is no landscape/fullscreen-rotation requirement.

### Reporting a problem

Record the app version/build shown by the About surface, the action you attempted, and the user-facing error category. Do not post cookies, authorization headers, signed media URLs, or other credentials in bug reports.
