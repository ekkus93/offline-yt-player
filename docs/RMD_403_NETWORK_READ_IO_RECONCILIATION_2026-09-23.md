# RMD-403 network-read I/O reconciliation

This evidence note records current-master implementation and qualification evidence for RMD-403 without replacing or compressing the canonical remediation checklist.

## Requirement mapping

RMD-403 requires remote response-body read failures to remain distinct from local filesystem failures, correct retryability for socket reset/timeout/truncation, deterministic fixture coverage for disconnect/timeout, and continued storage classification for local ENOSPC/write failures.

## Production-path evidence

- `core/src/download.rs::DownloadEngine::transfer` routes `response.read(...)` failures through `remote_body_read_error`, while local file open/truncate/seek/write/sync/rename and filesystem operations continue through `io_error`.
- `remote_body_read_error` maps timeout-like failures to retryable `ErrorKind::NetworkTimeout`, premature EOF to retryable `ErrorKind::IntegrityFailure`, and connection-aborted/reset/interrupted/not-connected/broken-pipe failures to retryable `ErrorKind::NetworkUnavailable`.
- `io_error` remains the local-storage mapper. OS error 28 maps to nonretryable `ErrorKind::InsufficientStorage`; other local filesystem failures map to nonretryable `ErrorKind::Internal`.
- The remote mapper emits bounded generic diagnostics rather than raw response/URL material, preserving the RMD-1302 redaction guarantee.

## Behavioral regression evidence

`core/src/download.rs` contains deterministic local fixture tests on the real `DownloadEngine::transfer` path:

- `mid_body_disconnect_is_retryable_remote_failure_not_storage` serves a declared body and closes the socket early, proving the failure is retryable and is not emitted as a storage error.
- `delayed_response_body_is_retryable_remote_failure_not_storage` uses a short request timeout against a delayed local response body, proving timeout/read failure remains retryable and outside the storage mapper.
- `remote_body_timed_out_io_error_maps_to_network_timeout` directly fixes the timeout classification contract.
- `local_enospc_still_maps_to_insufficient_storage` proves local ENOSPC remains a nonretryable storage failure.
- Existing successful fixture transfer tests exercise the local file-write path and final promotion alongside these failure regressions.

## Qualification strategy

This is Rust-core transfer classification behavior and does not require Android runtime to establish the defect fix. Per `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`, Rust behavioral tests are the primary proof, while repository CI, packaging, Android smoke, and API-35 timeout lanes guard integration drift.

Current master `11e2020268d0eb35ed59d341cd649c80e56534f5` contains the production implementation and regression coverage and passed exact-head CI run `35913786091`, Android smoke `35913786133`, and Android FGS timeout `35913786001`. The canonical TODO must be checked only after this reconciliation evidence itself is qualified, merged, and observed on post-merge master CI.
