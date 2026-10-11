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
