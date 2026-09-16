# Generic download engine audit

This audit maps OYP-501 through OYP-506 to the current portable Rust implementation and deliberately distinguishes implemented behavior from remaining qualification gaps.

## OYP-501 — HTTP transfer foundation

Implemented in `core/src/download.rs`:

- `DownloadPolicy` provides bounded connect and request timeouts; `DownloadEngine::new` applies both to the reqwest client.
- redirects are bounded with `reqwest::redirect::Policy::limited(8)`.
- `Content-Length`, `Range`, `206 Partial Content`, and `Content-Range` are handled explicitly.
- downloads use a sibling dot-prefixed `.partial` path and only rename to the final path after integrity checks.
- `validate_http_url` and `validate_relative_library_path` reject unsafe input before filesystem/network work.
- maximum asset size is bounded before and during transfer.

OYP-501 is implemented. The adversarial HTTP cases are qualified under OYP-506.

## OYP-502 — Pause/resume

Partially implemented:

- an existing partial file is durable continuation state.
- a resumed request sends `Range: bytes=<existing>-`.
- a valid `206` response is appended only when `Content-Range` begins at the expected byte.
- a server that ignores range and returns a normal success response causes the local partial to be truncated and the transfer to restart safely.

Still open:

- there is no persisted validator (ETag/Last-Modified or equivalent source identity) proving that an existing partial belongs to the same remote representation before append/reuse.
- pause orchestration is represented in the state machine but is not yet wired as a transfer-level cooperative pause channel.

Therefore OYP-502 must remain open.

## OYP-503 — Retry policy

Implemented in `core/src/download.rs`, `core/src/retry.rs`, and `core/src/state.rs`:

- failures are classified as retryable/permanent.
- `retry_delay` is bounded exponential backoff with bounded caller-provided jitter.
- `execute_with_retry` has a bounded attempt count and interruptible cancellation-aware backoff.
- `DownloadState::RetryWait` is durable/UI-visible state in the download state model.
- unit tests cover retry-to-success, permanent failure, attempt bounds, and cancellation.

OYP-503 is implemented.

## OYP-504 — Integrity and completion

Implemented:

- expected total size is validated when supplied.
- declared response body length is checked against bytes actually read.
- optional SHA-256 expectation is supported.
- SHA-256 is always computed for the completed partial.
- the final-path rename occurs only after size/body/checksum validation, so incomplete content is not promoted as completed.

OYP-504 is implemented.

## OYP-505 — Cleanup

Partially implemented:

- cancellation has an explicit retain/delete-partial policy.
- pre-canceled transfers terminate before networking.
- `cleanup_orphan_partials` recursively removes orphan `.partial` files.
- `preflight_space` exposes a typed insufficient-storage failure and ENOSPC maps to `InsufficientStorage`.

Still open:

- startup reconciliation is not yet connected to durable download-job state, so cleanup cannot distinguish resumable partials from true orphans at application startup.

OYP-505 remains open until startup reconciliation is wired.

## OYP-506 — Deterministic test server

OYP-506 is now implemented and automated with deterministic loopback HTTP fixtures. The test suite covers:

- normal transfer and expected-size verification,
- ranged resume with `206 Partial Content`,
- safe restart when a server ignores a range request,
- retry behavior driven by HTTP fixture responses,
- bounded request timeout surfaced as `NetworkTimeout`,
- mid-body interruption/cooperative cancellation,
- disconnect with deliberately incorrect `Content-Length`,
- preservation of incomplete data only as a partial file, never promotion to the final library path.

The final OYP-506 cases landed through PRs #79 and #80. Exact-head `master` CI run `35084986659` passed at `d3f24d2ab284fbb04d2b46ad420da0842d99a8bf` after those fixtures were merged.

## Next implementation order

1. Persist and validate resume representation identity before append.
2. Add cooperative pause semantics around the transfer loop.
3. Wire startup reconciliation against durable download-job records.
4. Reconcile completed OYP-500 checklist items in the canonical TODO once their implementation evidence is merged and green.

This ordering closes the remaining correctness risks before broadening UI/service integration.
