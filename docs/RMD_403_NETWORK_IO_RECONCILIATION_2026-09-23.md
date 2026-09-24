# RMD-403 network-read I/O reconciliation

This note records implementation and qualification evidence for RMD-403 on current `master`. It does not replace the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; the canonical TODO must still be reconciled after this evidence note is qualified and merged.

## Scope

RMD-403 requires the core download path to keep remote response-body read failures separate from local filesystem I/O failures, preserving retryability for remote failures while still mapping local storage failures to storage-appropriate errors.

## Implementation evidence

- `core/src/download.rs::DownloadEngine::transfer` maps `response.read(...)` through `remote_body_read_error` instead of the local `io_error` storage mapper.
- `core/src/download.rs::remote_body_read_error` maps timed-out body reads to retryable `ErrorKind::NetworkTimeout`.
- `remote_body_read_error` maps remote connection interruption/reset style failures to retryable `ErrorKind::NetworkUnavailable`.
- Unexpected EOF / declared-length truncation is surfaced as retryable `ErrorKind::IntegrityFailure` rather than local storage failure.
- Local filesystem failures still go through `core/src/download.rs::io_error`, which maps raw OS error 28 to `ErrorKind::InsufficientStorage` and other local storage failures to nonretryable internal/storage diagnostics.

## Regression coverage

The production download engine tests in `core/src/download.rs` cover the RMD-403 behavior directly:

- `mid_body_disconnect_is_retryable_remote_failure_not_storage` proves a remote mid-body disconnect is retryable and not reported as an internal/storage error.
- `remote_body_timed_out_io_error_maps_to_network_timeout` proves remote body read timeout classification is retryable `NetworkTimeout` and not a storage diagnostic.
- `delayed_response_body_is_retryable_remote_failure_not_storage` proves a stalled response body is treated as retryable remote failure, not storage I/O.
- `local_enospc_still_maps_to_insufficient_storage` proves local ENOSPC remains a nonretryable storage-capacity error.

## Qualification evidence

The RMD-403 implementation and tests are present on current `master` at `117e279485daa9c3dc56f8923f8bd29a9b169ea4`.

Current exact-head/post-merge qualification for that master commit passed:

- CI `35922727558` on exact `117e279485daa9c3dc56f8923f8bd29a9b169ea4`.
- Android smoke `35922727796` on exact `117e279485daa9c3dc56f8923f8bd29a9b169ea4`.
- API-35 FGS timeout qualification `35922727567` on exact `117e279485daa9c3dc56f8923f8bd29a9b169ea4`.

## Current canonical status

RMD-403 has production-path implementation and exact-head qualification evidence. The canonical TODO should be reconciled in a later detailed-TODO update that preserves the full checklist structure and cites this evidence. This evidence note alone must not be treated as canonical checkbox completion.
