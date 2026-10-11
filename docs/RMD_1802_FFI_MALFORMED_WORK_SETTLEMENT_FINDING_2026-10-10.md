# RMD-1802 FFI malformed-work settlement finding

At master `71cd8c872126e5358f12843b627ef0921e86105d`, `FfiDownloadWorkerService::execute_job_inner` rejects duplicate/blank asset identities before invoking `DownloadWorker`. This leaves an already persisted job in `QUEUED` rather than recording the worker's nonretryable `InvalidInput` failure. A scheduler retry can encounter the same malformed plan repeatedly.

Required correction: remove the redundant FFI preflight and rely on the worker's existing identity validation and durable `FAILED` settlement. Add file-backed FFI regression coverage for duplicate IDs, duplicate output paths and blank IDs. Assert no network transfer, staging or Library promotion. This is an open review finding; do not mark RMD-1802/RMD-1803 or final closeout complete.

Source-file writes through Ralph Bridge were blocked by tool safety checks in this run. No production fix or qualification is claimed.
