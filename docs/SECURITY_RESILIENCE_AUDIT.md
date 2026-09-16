# Security, privacy, and resilience audit

This audit maps OYP-1801 through OYP-1804 to the current implementation. It intentionally distinguishes qualified controls from integration work that remains open.

## OYP-1801 — Untrusted input hardening

Implemented and unit-qualified in `core/src/security.rs`:

- `validate_http_url` bounds URL length, accepts only HTTP(S) URLs with a host, and rejects embedded credentials.
- `sanitize_title` strips control characters, collapses whitespace, and bounds remote display titles.
- `sanitize_filename` replaces filesystem-hostile characters, strips unsafe leading/trailing dots/whitespace, handles empty/dot names, and bounds length.
- `validate_relative_library_path` rejects absolute paths, NULs, parent traversal, roots, and platform prefixes before download filesystem work.
- unit tests cover credential/non-HTTP URL rejection, metadata title sanitization, filename sanitization, and path traversal rejection.

OYP-1801 is implemented. Broader fuzz/adversarial expansion can strengthen it, but the TODO's four required controls are present and automated.

## OYP-1802 — Secret/log hygiene

Implemented and unit-qualified in `core/src/security.rs`:

- `redact_sensitive` redacts Authorization, Cookie, Set-Cookie, and API-key header values.
- URL query material is replaced with `REDACTED`, preventing signed query parameters/tokens from reaching diagnostic output through this sanitizer.
- tests assert representative header, cookie, and signed-query values do not survive redaction.

The YouTube adapter audit additionally requires provider diagnostics to use sanitized categories rather than signed URLs. OYP-1802 is implemented at the portable diagnostic boundary.

## OYP-1803 — Resource bounds

Implemented controls:

- `DownloadPolicy` defines bounded connect and request timeouts and `DownloadEngine::new` applies them to reqwest.
- redirects are bounded to eight hops.
- `max_asset_bytes` bounds expected size, declared response size, and bytes read during transfer.
- `preflight_space` exposes a typed insufficient-storage failure before transfer when platform free-space information is supplied.
- retry attempts and backoff are bounded by `execute_with_retry`/`retry_delay`.
- `DownloadConcurrencyGate` provides a process-local admission bound for transfer workers. It rejects a zero limit, blocks admission once the configured limit is active, supports non-blocking admission, and releases capacity through an RAII permit on every drop path. Unit tests prove the gate never admits more than the configured number of workers and that waiting work proceeds only after a permit is released.

All four OYP-1803 resource-bound requirements now have a portable implementation primitive. Durable queue scheduling remains an Android/service integration concern, but it must use this or an equivalently bounded admission mechanism rather than creating unbounded workers.

## OYP-1804 — Corruption/recovery

Implemented controls:

- the persistence repository detects missing/corrupt completed assets and exposes typed failures; see `docs/PERSISTENCE_LIBRARY_AUDIT.md`.
- SQLite migration logic rejects unsupported/newer schemas and transactionally applies supported migrations.
- incomplete downloads remain `.partial` until integrity checks succeed, so they are not promoted as completed library assets.

Still open:

- startup reconciliation between durable download-job state and partial files is not yet wired. This is the same open dependency recorded by OYP-505.
- application-level recovery behavior for every corrupt database/media case remains part of later E2E qualification.

Therefore OYP-1804 remains partially open.

## Reconciliation summary

The TODO may mark OYP-1801, OYP-1802, and OYP-1803 complete. OYP-1804 should remain open until startup reconciliation and recovery integration are qualified.
