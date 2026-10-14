# RMD-1802 — Download Setup thumbnail decoded-memory bound (2026-10-14)

## Finding
`ui/AppShell.kt::loadBoundedSetupThumbnail` already restricts the source to accepted HTTPS thumbnail hosts, disables redirects, and limits both declared and observed HTTP bytes. It previously called `BitmapFactory.decodeByteArray` directly after that check. Highly compressible images can carry large decoded dimensions despite small compressed payloads, so the HTTP byte cap alone does not bound the bitmap allocation.

## Production correction
`decodeBoundedSetupThumbnail` first probes dimensions using `BitmapFactory.Options.inJustDecodeBounds`, rejecting malformed/zero/negative sizes, either edge greater than 4,096 pixels, or more than 4,194,304 pixels. Multiplication is performed in `Long` to avoid integer overflow. Only an accepted image is decoded to ARGB_8888; returned bitmap dimensions are checked again and any nonconforming bitmap is recycled. Existing HTTP host, redirect, timeout, and compressed-byte limits remain unchanged. Rejected images fall back to the existing “Preview unavailable” UI.

## Behavioral proof and closeout status
`app/src/test/java/com/ekkus/offlineytplayer/ui/SetupThumbnailPolicyTest.kt` covers valid, invalid, excessive, and overflow dimensions. `app/src/androidTest/java/com/ekkus/offlineytplayer/AndroidRuntimeSmokeTest.kt` exercises the production decoder with a highly compressible 4,097 × 1 PNG (rejected) and a 2 × 2 PNG (accepted). This test is in the existing smallest Android smoke class; no new emulator lane is required.

**Not yet qualified at this writing:** exact implementation SHA and all required CI workflow IDs must be recorded after the commit and CI completion. RMD-1802 independent review, RMD-1803 final freeze, RMD-1804 merge verification, RMD-1805 engineering definition of done, and the separate human external policy/legal approval remain open.
