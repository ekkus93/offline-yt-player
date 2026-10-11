# RMD-1802 — Atomic durable completion and Library promotion (2026-10-11)

## Finding

After a worker claimed a job, checked network bytes and staged its managed media files, `DownloadWorker::execute_one` transitioned the in-memory snapshot to `Verifying` and wrote it unconditionally. It promoted Library metadata and staging records in another transaction, then wrote `Completed` unconditionally. A concurrently delivered pause/cancel could therefore be overwritten by Verifying/Completed, and a canceled work item could be promoted as a completed Library record.

## Correction

- `core/src/worker.rs::DownloadWorker::execute_one` uses an exact observed-snapshot compare-and-swap before entering Verifying. A failed compare aborts with `Canceled`, and the controller-preserved durable stop state is not reverted.
- `core/src/persistence.rs::LibraryStore::promote_completed_if_unchanged` checks the exact Verifying row and performs durable `Completed` settlement, Library metadata/asset promotion, and staging cleanup under a single SQLite write transaction. Competing pause/cancel cannot interleave, and a state mismatch returns false without promoting.
- `concurrent_cancel_cannot_promote_or_complete_a_stale_job` exercises a canceled competing state and asserts the queue remains Canceled, the Library has no promoted record, staging remains, and a matching Verifying state can complete and clear staging atomically.

Implementation: `27f82b4f6e26c27fadeb95fda9d5b148fb0e27e7`, rustfmt `ca91e6d887200daeac9fc603c42ea485e74371c3`.

## Qualification policy

The Rust formatting, Clippy, and workspace tests passed at the job level for `ca91e6d887200daeac9fc603c42ea485e74371c3` (CI run `38126525038`). The full eight-workflow matrix for the final documentation-inclusive head remains required, as do RMD-1801–1805 and RMD-GXX independent review/closeout requirements. No TODO box may be checked merely because the fix exists. External YouTube/service-policy/legal approval is separate.
