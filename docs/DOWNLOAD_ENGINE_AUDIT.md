# Generic download-engine audit

This audit maps the current Rust implementation and automated qualification to OYP-501 through OYP-506. Items that are not yet fully demonstrated remain explicitly open.

## OYP-501 — HTTP transfer foundation

`core/src/download.rs` configures bounded connect/request timeouts, an eight-hop redirect limit, Content-Length and Content-Range handling, a 64 GiB default asset bound, hidden `.partial` staging paths, and validated relative library paths. Source-generated filenames/paths pass the sanitizers in `core/src/security.rs`. The transfer code rejects oversized expected or declared bodies and verifies that ranged responses start at the requested offset.

## OYP-502 — Pause/resume

Continuation data is represented by the durable download snapshot persisted by `LibraryStore`, while partial bytes remain on disk. `DownloadEngine::transfer` discovers an existing partial, requests `Range: bytes=<existing>-`, and appends only when the server returns 206 with a matching Content-Range start. If a server ignores Range and returns a normal successful response, the engine truncates/restarts rather than appending incompatible bytes. Deterministic tests prove successful range reuse and reject a mismatched Content-Range without modifying or promoting the existing partial.

A separate explicit pause coordinator/UI action remains part of the Android service/FFI milestones; this audit closes the transfer-level continuation mechanics only.

## OYP-503 — Retry policy

`classify_error` separates retryable network/HTTP/source-change failures from permanent failures. `retry_delay` implements bounded exponential delay with bounded caller-supplied jitter. `DownloadState::RetryWait` and durable snapshots expose retry state and retry time to consumers. Unit tests prove the backoff bound, and the adversarial fixture proves a server-side 503 is surfaced as an explicitly retryable HTTP failure.

## OYP-504 — Integrity and completion

Transfers validate expected size when known, validate declared response length, calculate SHA-256 for every completed asset, optionally enforce an expected SHA-256, fsync the partial file, and rename it to the final path only after verification. Library promotion separately rejects incomplete items, so incomplete bytes cannot become completed library metadata through the supported path.

## OYP-505 — Cleanup

`DownloadPolicy.retain_partial_on_cancel` defines cancellation retention policy; `cleanup_orphan_partials()` recursively removes staged partials. Disk-full errno 28 maps to `InsufficientStorage`, and `preflight_space()` provides deterministic expected-vs-available storage rejection. Pre-canceled transfers are rejected before networking (qualified by the regression merged in PR #50).

Startup orchestration that decides which persisted jobs/partials to resume or remove remains an OYP-1004/OYP-1804 concern and is not claimed complete here.

## OYP-506 — Deterministic test server

The Rust suite uses local loopback fixture servers only; it does not depend on live external services. The `tiny_http` fixture covers successful transfer and ranged resume, while `core/tests/download_adversarial.rs` covers bounded timeout, abrupt disconnect, incorrect/truncated Content-Length, retryable HTTP failure, and mismatched Content-Range rejection. The deterministic E2E fixture flow separately exercises interruption/restart and ranged continuation. Together these cover the OYP-506 adversarial server matrix.

## Reconciliation guidance

OYP-501, OYP-503, OYP-504, and OYP-506 can be reconciled as implemented. OYP-502 can reconcile its persisted/ranged-resume/fallback/verification mechanics while retaining platform pause-orchestration obligations in the FFI/service tasks. OYP-505 can reconcile cancellation policy, orphan cleanup, and storage-pressure handling; startup reconciliation remains open under OYP-505/OYP-1004/OYP-1804. This avoids converting transfer-layer evidence into false platform-orchestration claims.
