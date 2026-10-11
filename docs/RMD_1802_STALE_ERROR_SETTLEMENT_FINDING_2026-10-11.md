# RMD-1802: stale worker error settlement (2026-10-11)

Independent Rust review found that `DownloadWorker::finish_failed_or_retry` and `finish_canceled` in `core/src/worker.rs` still call unconditional `LibraryStore::save_download_snapshot`. After a worker claims a job, a concurrent Pause/Cancel or newer claimed attempt can be overwritten by a stale worker failure or external-cancel handler. An error after chunk progress can also replace newer durable byte counts with stale in-memory values.

Required correction: settle only an active, same-attempt durable claim using reread-and-compare-and-swap; preserve latest durable progress; suppress false success reports when settlement loses the race. Add deterministic regressions for Pause, Cancel, reclaimed attempt, and progress preservation. Keep RMD-1802/1803 and engineering closeout unchecked until the fix is qualified on exact-head CI.
