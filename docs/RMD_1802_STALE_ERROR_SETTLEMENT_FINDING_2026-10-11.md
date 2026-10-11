# RMD-1802: stale worker error settlement (2026-10-11)

Independent Rust review found that `DownloadWorker::finish_failed_or_retry` and `finish_canceled` in `core/src/worker.rs` still call unconditional `LibraryStore::save_download_snapshot`. After a worker claims a job, a concurrent Pause/Cancel or newer claimed attempt can be overwritten by a stale worker failure or external-cancel handler. An error after chunk progress can also replace newer durable byte counts with stale in-memory values.

Required correction: settle only an active, same-attempt durable claim using reread-and-compare-and-swap; preserve latest durable progress; suppress false success reports when settlement loses the race. Add deterministic regressions for Pause, Cancel, reclaimed attempt, and progress preservation. Keep RMD-1802/1803 and engineering closeout unchecked until the fix is qualified on exact-head CI.

## Exact-head baseline qualification

The existing unfixed baseline `2528244a63a8d46a9e93f49abd5573562919ea8a` passed all eight workflows on 2026-10-11: CI `38126967309`, Android smoke `38126967203`, Android FGS timeout `38126967236`, Supply chain `38126967219`, CI evidence `38126967229`, Deterministic E2E fixture `38126967262`, Android cold start `38126967172`, and Android real-network E2E `38126967181`. These passes establish the baseline only; they do **not** qualify the stale-settlement fix, which is not yet committed.

## Required implementation invariants

Error/cancel settlement must reread the durable row and require the original claimed attempt number and an active state (Resolving, Downloading, or Verifying) before a compare-and-swap update. The transition must be based on the latest durable row, retaining any newer progress bytes and total-byte estimate. A concurrent Paused/Canceled row must remain untouched; a reclaimed/newer attempt must not receive an outcome from the old worker. Reported failed/retry/canceled outcomes must reflect a successful settlement or an authoritative same-attempt durable stop, never an unsuccessful stale write. Cover all of these cases with deterministic Rust regressions, then run the full exact-head matrix again.

## Implemented correction and qualification (2026-10-11)

The stale error/cancel settlement correction is committed directly on `master` at exact implementation SHA `d8ce243cc347beaf88d1e29194520c0792c32286` (`core/src/worker.rs`). `settle_active_claim` reloads the durable snapshot, requires the original attempt and an active state, and uses `LibraryStore::replace_download_snapshot_if_unchanged` to preserve concurrent control decisions and the latest progress/total-byte values. `finish_failed_or_retry` and `finish_canceled` return whether the durable CAS actually succeeded; the worker report suppresses false failed/retry/canceled outcomes after a lost race. `durable_stop_for_claim` recognizes a persisted Pause/Cancel only for the originally claimed attempt.

Deterministic Rust regressions in `core/src/worker.rs` are `stale_error_and_cancel_handlers_preserve_user_pause_and_cancel`, `reclaimed_attempt_cannot_receive_stale_settlement_or_stop_report`, and `settling_failure_or_cancel_keeps_latest_progress_and_total_bytes`. These exercise user Pause/Cancel preservation, stale-attempt rejection, and preservation of later durable byte/total estimates across Failed, RetryWait, and Canceled outcomes.

**Exact-head CI on `d8ce243cc347beaf88d1e29194520c0792c32286`: all eight workflows passed**: CI `38132996804`, Android smoke `38132996795`, Android FGS timeout `38132996780`, Supply chain `38132996799`, CI evidence `38132996815`, Deterministic E2E fixture `38132996757`, Android cold start `38132996776`, and Android real network E2E `38132996821`.

This qualifies the identified stale-settlement correction on its implementation head, not the entire independent code review or frozen final release candidate. The canonical RMD-1802 review, RMD-1803 final exact-head qualification, RMD-1804 verification, and RMD-1805 engineering closeout remain unchecked until their separate requirements are established and reconciled. The external YouTube/service-policy/legal approval remains separate and unapproved.
