# RMD-1802 — Unknown-length download percentage production mapping (2026-10-13)

## Independent review finding

The portable `DownloadRowPresentation.progressPercent` correctly returns null when `totalBytes` is unknown, and its policy test passed. The actual production `MainActivity.toDownloadsScreenState` instead called `boundedDownloadPercent` with a null denominator, which returned integer zero. `DownloadScreenPolicy.detail` unconditionally displayed that integer as `0%`. An active unknown-length transfer therefore appeared to have a measured zero-percent completion value, contrary to the RMD-507 requirement.

## Correction

`DownloadRowModel.percent` is nullable. The production `CoreDownloadSnapshot` mapper sets a percentage only for a positive known total, and the actual Downloads Compose detail shows `Progress unknown` otherwise. It continues to show the transferred-byte count and preserves all queue states, IDs, controls, speed and ETA fields.

## Qualification

Host-JVM `DownloadPresentationMappingTest` checks null, zero, negative and positive denominators through the production snapshot mapper and actual Downloads detail policy. Packaged `ProductionComposeBehaviorTest.unknownLengthDownloadsRenderActualBytesWithoutInventingZeroPercent` renders the real Downloads screen and asserts that unknown progress and actual bytes are visible but a fabricated `0%` is absent.

## Closeout

Reopen the RMD-507 unknown-length checkbox until all eight exact-head workflows pass and the canonical TODO is reconciled with their IDs. RMD-1802 independent review, RMD-1803 final freeze, RMD-1805 engineering closeout and external service/legal approval remain open.
