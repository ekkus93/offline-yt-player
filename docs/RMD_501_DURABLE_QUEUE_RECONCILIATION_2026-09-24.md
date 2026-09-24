# RMD-501 durable queue source-of-truth reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-501.

## Current-master production evidence

Current master `81c6635acc42407e2cc6b60cf4c56ead3728c0e7` contains the durable queue implementation required by RMD-501.

- `core/src/state.rs` defines the durable lifecycle states `Queued`, `Resolving`, `Downloading`, `Paused`, `RetryWait`, `Failed`, `Verifying`, `Completed`, and `Canceled`, together with the legal transition contract. `RetryWait` is the durable waiting/retrying state.
- `core/src/events.rs::DurableDownloadSnapshot` is the durable queue record exposed throughout the core.
- `core/src/persistence.rs` stores queue snapshots in SQLite `download_jobs`, including state, byte progress, total bytes, attempt count, next retry eligibility, and structured error; `save_download_snapshot` and `load_download_snapshots` persist/reconstruct that state across process death.
- `core/src/ffi.rs::FfiCoreService::download_queue` exposes the persisted snapshots through UniFFI as `FfiDurableDownloadSnapshot` / `FfiDownloadState`, making the durable core queue the platform-visible state source.
- `core/src/ffi_download_control.rs` implements enqueue/pause/resume/cancel/retry by loading and mutating the durable snapshot rather than maintaining a service-local queue state. Its tests prove enqueue persistence, legal persisted transitions, reopen/process-death survival, and explicit retry semantics.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` routes control actions through `GeneratedUniffiDownloadControlGateway` using the durable queue item id. It does not maintain an in-memory authoritative queue-state boolean or state machine; its constants describe Android service capabilities/runtime policy rather than replacing the persisted queue.

## Requirement mapping

1. Durable queued/active/paused/waiting/retrying/failed/cancelled/completed lifecycle is represented by the core state machine and exposed through `download_queue`; transient `Resolving`, `Downloading`, and `Verifying` are the active phases, while `RetryWait` is waiting/retrying.
2. Queue state survives process death because snapshots live in SQLite and are reconstructed through `load_download_snapshots`; reopen survival is covered directly by control and process-death tests.
3. Android service controls mutate the durable core record through the generated UniFFI gateway rather than a service-local authoritative queue state.

## Qualification policy

This is primarily durable-core/FFI state proof, so the Android qualification acceleration plan places the authoritative behavior in deterministic Rust/JVM tests while retaining the Android smoke lane as integration evidence. The canonical TODO remains the completion source of truth and should be checked only after this evidence commit passes exact-head qualification, merges to `master`, and the canonical TODO is reconciled with the merged evidence.
