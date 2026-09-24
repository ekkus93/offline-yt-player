# RMD-507 progress/speed/ETA implementation evidence — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-507.

## Production-path changes

- `core/src/download.rs` now exposes `DownloadEngine::transfer_with_progress`, which reports transferred bytes after each response-body chunk is durably written to the partial file. Existing callers can still use `transfer` with a no-op progress callback.
- `core/src/worker.rs` wires the production worker path to that callback. Observed transfer progress updates `bytes_downloaded`, calculates bounded-window `bytes_per_second` and `eta_seconds` through `TransferMetricEstimator`, and persists coalesced snapshots through `ProgressCoalescer`.
- `core/src/persistence.rs` migrates the queue schema to persist the latest live metrics separately from the durable lifecycle fields, so queue state remains process-reconstructable while progress metrics are exposed to the app.
- `core/src/ffi.rs` exposes speed/ETA through `FfiDurableDownloadSnapshot` / `download_queue`.
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` maps generated metric fields into app download snapshots.
- `MainActivity.toDownloadsScreenState` renders speed labels only for positive speeds and ETA labels only when total bytes are known.

## Behavioral proof added

- `core/src/worker.rs` tests prove known-length progress records bounded-window speed and ETA.
- `core/src/worker.rs` tests prove unknown-length progress records speed but never fabricates ETA.
- `core/src/ffi.rs` tests prove metrics cross the FFI-visible queue model.

## Qualification policy

This is primarily portable-core/FFI state behavior, so deterministic Rust/JVM tests are the primary fast proof under the Android qualification acceleration plan. Android smoke and API-35 timeout qualification remain required exact-head integration evidence before merge.
