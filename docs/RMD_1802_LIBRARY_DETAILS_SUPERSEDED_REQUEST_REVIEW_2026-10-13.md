# RMD-1802 — Superseded Library Details request review (2026-10-13)

## Production finding

The Library Details callback in `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` launches blocking `AppLibraryDetailsGateway.getDetails(row.id)` on `Dispatchers.IO`. It previously validated the returned `itemId` against the *originally* clicked row but did not verify that the user had not selected another row while IO was pending. A late first response could therefore replace newer details or status.

## Correction

Each Details selection increments a Compose-held request generation. The callback captures that generation before dispatching IO, then ignores stale successes and stale exception messages. Existing returned-item-ID validation and repository semantics remain unchanged.

## Behavioral qualification

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt::libraryDetailsDoesNotPublishSupersededGatewayResponse` renders two rows, blocks the first gateway call, selects the second row, observes its actual repository detail in Compose, releases the first call, and verifies that the old metadata does not replace the current selection.

## Closeout state

Implementation and packaged regression added on 2026-10-13. The exact-head eight-workflow CI matrix is pending. RMD-1802, RMD-1803, RMD-1805, and external YouTube/service-policy/legal approval remain open.
