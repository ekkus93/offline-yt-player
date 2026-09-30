# RMD-502a Android runtime reconciliation — 2026-09-30

## Result

RMD-502a is implementation-complete and qualified on exact `master` SHA `682e7dda55987046ce3bb1694a377d98b0c9dd3b`.

## Production-path evidence

The durable worker attachment uses provider-neutral executable-plan persistence in `core/src/durable_work.rs`, the generated UniFFI worker boundary, and the shared app-private `offline-yt-player.sqlite3` database. Android runtime launch points invoke the real worker executor rather than merely owning notification/lifecycle state.

API 26–33 qualification routes a deterministic durable fixture through `AndroidDownloadExecutionScheduler`, requires the foreground-service fallback, and proves scheduler-launched work reaches terminal `COMPLETED` durable queue state, creates one completed Library item, and writes the expected local media asset.

API 34+ qualification routes a deterministic durable fixture through the same production scheduler, requires `UserInitiatedDataTransferJob` selection, allows `DownloadUserInitiatedJobService` to invoke the real `DownloadWorkerExecutor`, and proves the same terminal durable queue, Library, and local-asset outcomes.

Together these tests close the Android execution-attachment gap recorded in the canonical remediation TODO: both supported API families now schedule executable durable work through their real Android launch point into the core worker loop and completed durable Library state.

## Exact-head qualification

Exact master `682e7dda55987046ce3bb1694a377d98b0c9dd3b` passed the complete currently configured six-workflow matrix:

- CI: `36676333860`
- Android smoke: `36676333845`
- Android FGS timeout / API-35 UIDT qualification: `36676333767`
- Supply chain: `36676333779`
- CI evidence: `36676333747`
- Deterministic E2E fixture: `36676333873`

All six runs completed successfully. This evidence is sufficient to reconcile the RMD-502a checkbox in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; it does not by itself close the broader RMD-1500 end-to-end requirements.
