# OYP-505 cleanup audit

This audit records the implemented download cleanup and recovery boundary.

## Cancel behavior policy

`DownloadPolicy.retain_partial_on_cancel` explicitly controls cancellation retention. `DownloadEngine::transfer` checks cancellation before networking and cooperatively while copying. When retention is disabled it removes both the `.partial` file and its resume-representation sidecar; when enabled it preserves resumable bytes for a later restart. The durable download state machine separately models `Canceled` as a terminal state and the coarse FFI control service persists legal cancel transitions.

## Orphan partial-file cleanup

`DownloadEngine::cleanup_orphan_partials` recursively visits the configured library root, removes only files whose names end in `.partial`, and clears the associated resume sidecar. The traversal is bounded to the configured root and does not interpret arbitrary external paths.

## Startup reconciliation

`reconcile_startup_downloads` reconstructs durable queue truth after process death. Snapshots in `Resolving`, `Downloading`, or `Verifying` cannot truthfully remain active without a live worker, so they are moved to `Queued`, their stale retry deadline is cleared, and a retryable interruption diagnostic is persisted. User-paused jobs, durable retry waits, queued jobs, failures, and terminal states remain unchanged. Tests cover every `DownloadState` and prove reconciliation is idempotent.

## Storage-pressure handling

`DownloadEngine::preflight_space` returns typed `InsufficientStorage` before transfer when known expected bytes exceed known available bytes. During filesystem writes, OS ENOSPC (`raw_os_error() == 28`) is also mapped to the same typed error, so storage exhaustion that occurs after preflight cannot be misreported as a generic network or integrity failure.

## Qualification

The download-engine tests cover cancellation, storage preflight, safe partial handling, and deterministic transfer behavior. Startup-reconciliation tests cover the complete state set and repeated invocation. Rust fmt/clippy/test, Android qualification, and UniFFI/Android ABI CI provide the repository-wide exact-head gate.

These implementation and test boundaries satisfy all four explicit OYP-505 checklist requirements. Canonical TODO reconciliation should mark OYP-505 complete after this audit's exact-head CI passes and the change is merged.
