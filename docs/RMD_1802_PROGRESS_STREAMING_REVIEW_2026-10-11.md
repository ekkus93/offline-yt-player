# RMD-1802 — Real chunk-level durable progress review (2026-10-11)

## Finding

Previous `DownloadWorker::execute_one` only updated `bytes_downloaded` after `DownloadEngine::transfer` returned a completed asset. Consequently a single large media asset could show zero progress until completed, even though the existing bounded-speed/ETA tests succeeded. The prior synthetic one-second-per-asset estimate was removed in earlier remediation, but monotonic timing alone could not supply intermediate byte samples.

## Production correction

- `core/src/download.rs::DownloadEngine::transfer_with_progress` calls a fallible observer after each successfully written response-body chunk. `transfer` remains a compatible no-observer wrapper. Resumed bytes are included in the reported per-asset total.
- `core/src/worker.rs::DownloadWorker::execute_one` converts these into monotonic-clock observations, coalesces events, and atomically updates the durable snapshot with `LibraryStore::replace_download_snapshot_if_unchanged`. A superseding pause/cancel prevents stale progress writes. It flushes final asset bytes before staging.
- The new tests `progress_callback_reports_intermediate_written_bytes` and `worker_persists_chunk_progress_before_single_asset_completion` verify intermediate progress on a multi-chunk transfer; existing known/unknown-length tests cover bounded speed/ETA behavior.

Implementation commits: `837f703502eef16d6eb8bbb953798e48e5a139e6`, rustfmt follow-up `e3089161c730f1d8d21037ae1b0dae312767c201`.

## Qualification / closeout policy

The Rust formatting, Clippy, and unit-test job passed for `e3089161c730f1d8d21037ae1b0dae312767c201` (normal CI run `38125476011`), but at this note's creation full Android smoke/cold-start/FGS/real-network and supply-chain exact-head acceptance remained pending. Do not mark final engineering completion or RMD-1802/RMD-1803 closed until the new frozen head passes the complete required matrix, final independent production review is finished, and the canonical TODO is reconciled. Human YouTube/service-policy/legal approval remains separate.

## Additional RMD-1802 resource-bound finding — 2026-10-13

Independent review of `core/src/events.rs::TransferMetricEstimator` found that the age-only pruning preserved every sample emitted inside the same monotonic timestamp or recent time window. `DownloadWorker::execute_one` feeds this estimator after every successfully written body chunk, making the sample deque grow proportional to chunk count under high callback rates despite the "bounded samples" guarantee. Production now imposes a hard cap of 256 recent samples in addition to the elapsed-time window. Deterministic Rust regression `high_rate_chunk_callbacks_cannot_grow_metric_history_without_bound` feeds 10,000 same-timestamp callbacks, verifies the cap throughout and after a later sample, and verifies unknown totals continue to suppress ETA. See `docs/RMD_1802_TRANSFER_METRIC_SAMPLE_BOUND_REVIEW_2026-10-13.md`. Do not claim this focused correction is qualified until new exact-head CI passes; RMD-1802 independent review, RMD-1803 final freeze, global closeout and external human release approval remain open.
