# RMD-1802 — Empty/non-playable download-plan finding (2026-10-10)

**Status:** remediation committed to master; exact-head qualification pending. Do not close RMD-1802, RMD-1803, RMD-1805, or global closeout.

## Finding

An empty asset list was accepted by durable work persistence, and the Rust worker could promote the corresponding item as Completed with no playable media. Plans containing only thumbnail/subtitle assets had the same false-completion risk.

## Production remediation

- `core/src/durable_work.rs`: `DurableDownloadWorkStore::save` rejects plans without at least one audio or video asset before writing executable work or presentation metadata.
- `core/src/worker.rs`: `DownloadWorker::execute_one` rejects the same malformed plans before transfer or Library promotion.
- Implementation commits: `5ceeeedfe215748070de8a9f55b6c97e95c68bfe` and `18960b5dd0bf8339a0ebf4126837ae0816b28cd7` (direct to master).

## Behavioral regression tests

- `empty_asset_plan_is_rejected_before_work_or_presentation_is_persisted`
- `thumbnail_only_plan_cannot_be_persisted_as_executable_media`
- `empty_plan_fails_without_promoting_an_unplayable_library_item`
- `thumbnail_only_plan_cannot_complete_as_playable_media`

These assert rejection, absence of durable executable/presentation records, terminal Failed state, and no fabricated Library item.

## Remaining acceptance

Record exact-head passing CI for the final documentation/code candidate; complete independent Rust/Android production-path review and the remaining RMD-1800 closeout checklist. The external service-policy/legal release gate remains separate.
