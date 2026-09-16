# Generic Download Engine Audit

This audit maps OYP-501 through OYP-506 to the implementation on `master` as of the source-abstraction closeout. It intentionally distinguishes implemented behavior from qualification that is still missing.

## OYP-501 — HTTP transfer foundation

Implemented in `core/src/download.rs`:

- `DownloadPolicy` provides bounded connect and request timeouts.
- `reqwest::redirect::Policy::limited(8)` bounds redirect following.
- `TransferRequest` carries expected size and checksum metadata.
- `DownloadEngine::transfer` validates `Content-Length`, supports `Range`, and validates the starting offset of `Content-Range` before appending.
- Transfers use hidden sibling `.partial` files and atomically rename only after validation.
- `validate_relative_library_path` and the source adapter's filename sanitization keep remote metadata from becoming arbitrary filesystem paths.

The existing fixture tests cover normal transfer and range resume. The implementation portion of OYP-501 is complete.

## OYP-502 — Pause/resume

The portable continuation primitive is a durable partial file plus persisted `DurableDownloadSnapshot` state in `LibraryStore`. Existing tests prove that a pre-existing partial is resumed with a ranged request and that the final bytes equal the fixture payload.

When a server does not return `206 Partial Content`, the engine deliberately truncates/restarts the partial rather than appending incompatible bytes. When it does return `206`, the `Content-Range` start must exactly equal the existing partial length or the transfer fails with `IntegrityFailure`.

This qualifies safe ranged continuation and fallback behavior. Higher-level pause/resume orchestration through the still-open FFI/download-service surface remains separate work.

## OYP-503 — Retry policy

`classify_error` separates retryable network/HTTP/source-change categories from permanent failures. `retry_delay` implements bounded exponential backoff with caller-supplied bounded jitter and has deterministic unit coverage. `DurableDownloadSnapshot` contains `RetryWait`, attempt count, retry deadline, and typed last error, making retry state persistable and consumable by UI/service layers.

The policy primitives are implemented. End-to-end retry orchestration remains part of the higher-level service/FFI work.

## OYP-504 — Integrity and completion

`transfer` validates expected byte count when known, validates declared response length, computes SHA-256 for every completed transfer, optionally compares an expected SHA-256, and renames `.partial` to the final path only after those checks. `LibraryStore::promote_completed` separately refuses incomplete items. These layers prevent a partial transfer from becoming a completed library record.

## OYP-505 — Cleanup

Implemented primitives include configurable retain/remove-partial-on-cancel policy, recursive orphan `.partial` cleanup, durable download snapshots/staged asset IDs for startup reconciliation, disk-space preflight, and ENOSPC mapping to `InsufficientStorage`.

One edge remains: a transfer whose cancellation token is already set still constructs/sends the HTTP request before cancellation is observed in the copy loop. That should be hardened with a pre-request cancellation check and deterministic regression test before OYP-505 is fully closed.

## OYP-506 — Download test server

The Rust test suite contains a deterministic loopback HTTP fixture server with range support. Current automated tests cover successful transfer and resume. Broader deterministic fault injection for timeout, disconnect, malformed/incorrect content length, and retry orchestration is not yet present, so OYP-506 remains open.

## Closeout status

OYP-501 and OYP-504 are implementation-complete. OYP-502 and OYP-503 have the portable primitives but depend on higher-level orchestration for full milestone closeout. OYP-505 needs pre-cancel hardening. OYP-506 needs the remaining fault-injection matrix. This document must not be used to mark those still-open obligations complete without the corresponding code/tests.