# RMD-1802 — Stale expanded Library Details after successful removal (2026-10-13)

## Finding

The production `LibraryScreen` retained `selectedDetails` after `AppLibraryMutationGateway.removeLibraryItem` returned success. An already-open Details panel therefore continued displaying the deleted item's persisted metadata after the Remove action succeeded. Moreover, an earlier asynchronous `getDetails` completion could repopulate the panel after removal because the request-generation guard was not invalidated by deletion.

## Correction

On a successful, confirmed Remove response, increment `detailsRequestGeneration`, clear `selectedDetails`, and clear `pendingRemove` on the Compose thread. Preserve the existing error/failure handling and do not clear details when removal is unsuccessful. No change to the durable mutation arguments or storage deletion logic.

## Behavioral qualification

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt::libraryRemovalClearsExpandedDetailsAndInvalidatesStaleSelection` opens the actual Library Details panel through `AppLibraryDetailsGateway`, invokes the real rendered Remove/Confirm controls through `AppLibraryMutationGateway`, checks the correct item ID and confirmed flag, and asserts the old Details panel is absent after the successful mutation.

## Closeout status

Implementation and test added on `master`; do not claim qualification until all eight standard workflows pass on the exact implementation SHA. RMD-1802 independent review, RMD-1803 final freeze, RMD-1805 engineering closeout and external YouTube/service/legal approval remain open.
