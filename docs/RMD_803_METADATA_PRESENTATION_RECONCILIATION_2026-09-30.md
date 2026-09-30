# RMD-803 Metadata Presentation Reconciliation — 2026-09-30

## Result

RMD-803 metadata presentation is implemented and exact-head qualified on `master` at `b51cb3ad839e6f235755f1789609e395fac16b0c`.

This document supersedes the earlier open-blocker notes in:

- `docs/RMD_803_METADATA_PRESENTATION_AUDIT_2026-09-30.md`
- `docs/RMD_803_METADATA_BOUNDING_QUALIFICATION_2026-09-30.md`

Those earlier notes were correct when written: production Downloads rows still rendered durable job identifiers as user-facing titles. The current implementation has since closed that gap.

## Production implementation evidence

### Persisted/resolved metadata in production screens

- Source analysis and Add/Setup continue to consume provider-neutral resolved metadata through `GeneratedUniffiSourceAnalysisGateway` and `SourceMetadataPolicy`, as already qualified under RMD-702.
- Library rows continue to map persisted `CoreLibraryItem.displayTitle`, quality, duration, and local playback-asset availability through `MainActivity.toLibraryScreenState(...)`; display titles and quality labels are normalized by `SourceMetadataPolicy`.
- Downloads rows no longer render the durable `jobId` as the user-facing title. `DurableDownloadWorkStore.save(...)` now persists a `download_presentations(job_id, display_title)` row from the resolved `DownloadPlan.title`. This presentation row deliberately survives deletion of executable work so completed Downloads rows retain resolved metadata after worker completion.
- `FfiDownloadWorkerService.download_presentations()` exposes provider-neutral `(job_id, display_title)` records through UniFFI.
- Android `DownloadPresentationGateway.titlesByJobId()` reads those generated-UniFFI presentation records.
- `MainActivity.bootstrapProductionUi()` joins `listDownloadQueue()` snapshots to `DownloadPresentationGateway.titlesByJobId()` both for initial state and for refreshes, then calls `toDownloadsScreenState(...)` with the resolved-title map.
- `toDownloadsScreenState(...)` now renders `SourceMetadataPolicy.title(titlesByJobId[snapshot.jobId].orEmpty())` rather than `SourceMetadataPolicy.title(snapshot.jobId)`.

### Metadata bounds and fake-data removal

- `SourceMetadataPolicy` remains the shared metadata boundary for titles, quality labels, channel/source identity, descriptions/diagnostics, and control-character removal.
- `MetadataPresentationPolicy` delegates compact title and quality normalization to `SourceMetadataPolicy`, bounds source identity, bounds long detail labels/values, and keeps long details in a deterministic dedicated region rather than an unbounded primary-screen metadata wall.
- Preview/test fixture data remains confined to tests/previews; production Add/Share/Downloads/Library presentation now uses generated-core/gateway state and durable metadata rather than hard-coded production values.

## Behavioral and regression coverage

- `core/src/durable_work.rs` tests prove executable work persists/reopens, presentation metadata persists alongside it, and presentation metadata remains after executable work deletion.
- `app/src/test/java/com/ekkus/offlineytplayer/ui/MetadataPresentationPolicyTest.kt` covers ordinary compact metadata, malformed/blank values, oversized title/quality/source/detail fields, and deterministic long-detail presentation.
- `app/src/test/java/com/ekkus/offlineytplayer/ShareIntentPolicyTest.kt` guards the production `MainActivity` wiring: generated core/download/library/source gateways are opened off-main-thread, `DownloadPresentationGateway.open(databasePath)` is present, initial queue mapping uses `listDownloadQueue().toDownloadsScreenState(initialTitles)`, and refresh mapping uses `result.toDownloadsScreenState(titles)`.
- Existing `DownloadRowPolicyTest` and `DownloadsOperationalScreenTest` continue to cover Downloads row state, progress, size, error, speed/ETA, filters, and legal action presentation without depending on job IDs as titles.

## Exact-head qualification

Exact master `b51cb3ad839e6f235755f1789609e395fac16b0c` passed all six required workflows:

- CI `36756073588`
- Android smoke `36756073839`
- Android FGS/API-35 UIDT `36756073605`
- Supply chain `36756073712`
- CI evidence `36756073819`
- Deterministic E2E fixture `36756073407`

## Canonical TODO status

RMD-803 is ready for canonical checkbox reconciliation. The canonical TODO should update the four RMD-803 checkboxes from unchecked to checked and cite this document plus the exact-head qualification above.
