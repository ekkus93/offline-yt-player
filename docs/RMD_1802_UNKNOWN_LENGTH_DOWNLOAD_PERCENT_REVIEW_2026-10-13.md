# RMD-1802 — Unknown-length download percentage production mapping (2026-10-13)

## Independent review finding

The portable `DownloadRowPresentation.progressPercent` correctly returns null when `totalBytes` is unknown, and its policy test passed. The actual production `MainActivity.toDownloadsScreenState` instead called `boundedDownloadPercent` with a null denominator, which returned integer zero. `DownloadScreenPolicy.detail` unconditionally displayed that integer as `0%`. An active unknown-length transfer therefore appeared to have a measured zero-percent completion value, contrary to the RMD-507 requirement.

## Correction

`DownloadRowModel.percent` is nullable. The production `CoreDownloadSnapshot` mapper sets a percentage only for a positive known total, and the actual Downloads Compose detail shows `Progress unknown` otherwise. It continues to show the transferred-byte count and preserves all queue states, IDs, controls, speed and ETA fields.

## Qualification

Host-JVM `DownloadPresentationMappingTest` checks null, zero, negative and positive denominators through the production snapshot mapper and actual Downloads detail policy. Packaged `ProductionComposeBehaviorTest.unknownLengthDownloadsRenderActualBytesWithoutInventingZeroPercent` renders the real Downloads screen and asserts that unknown progress and actual bytes are visible but a fabricated `0%` is absent.

## Closeout

Reopen the RMD-507 unknown-length checkbox until all eight exact-head workflows pass and the canonical TODO is reconciled with their IDs. RMD-1802 independent review, RMD-1803 final freeze, RMD-1805 engineering closeout and external service/legal approval remain open.

## Exact-head Android smoke regression and repair (2026-10-13)

Exact `master` `8eed27ce67c02f9336bab8acfbec6e7084fbc0c5` passed CI `38410241442`, FGS timeout `38410242391`, Supply chain `38410242119`, CI evidence `38410241849`, deterministic fixture E2E `38410241293`, Android cold start `38410242055`, and Android real-network E2E `38410241681`, but Android smoke `38410241587` failed in `Rmd1507NotificationControlInstrumentedTest.notificationActionsDriveDurableQueueAndProductionDownloadsUi` (Compose timeout at line 78). The instrumentation still expected `Paused · 0%`, `Active · 0%`, and `Canceled · 0%` for a fixture with unknown total bytes. The production correction deliberately renders `Progress unknown` instead. Update the three real notification-control UI assertions to require the new truthful state text, preserving actual notification PendingIntent, durable queue, and Compose behavior. This is a test expectation correction, not evidence that the failed run passed; require a fresh all-eight exact-head matrix before checking RMD-507 or RMD-1800 closeout.
