# RMD-1802 — Library and Downloads row presentation review (2026-10-13)

## Production finding

The expanded Library Details panel and per-download error already used `SourceMetadataPolicy`, but the default `LibraryItemRow` displayed persisted `LibraryRowModel.title/detail` verbatim. `DownloadRow` and `DownloadScreenPolicy.detail` likewise displayed persisted download titles, state labels, size, speed and ETA labels without the shared redaction/length bound. Library Remove confirmation, missing-details notices, Downloads controls and Details notices also echoed raw row titles. A provider-controlled signed URL or credential-bearing HTTP diagnostic in one of these fields could be shown to the user even when the expanded Details view was safe.

## Production correction

`app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` applies `SourceMetadataPolicy.title` to row titles and `SourceMetadataPolicy.diagnostic` to secondary labels at their Compose presentation boundary. The same title projection is used in user-facing action/status messages and the editable rename default. This does not rewrite persisted metadata, queue IDs, download state or gateway arguments.

## Behavioral qualification

`ProductionComposeBehaviorTest.libraryAndDownloadRowsRedactPersistedProviderMetadataInRealCompose` renders actual Library and Downloads Compose screens with synthetic signed URL credentials in every affected row field. It verifies bounded/redacted titles and details, no raw token in either screen or Remove confirmation, and a real Pause dispatch to the same download control gateway with the token still absent.

## Closeout state

This is a newly identified independent-review defect and a source/packaged-regression correction, not a release signoff. Run all eight exact-head workflows and preserve RMD-1802/1803/1805/global closeout and external service/legal approval as open until independent review and final qualification are complete.
