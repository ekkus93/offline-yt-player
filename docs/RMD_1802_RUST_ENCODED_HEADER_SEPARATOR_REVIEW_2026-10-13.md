# RMD-1802 — Rust percent-encoded credential-header separator review (2026-10-13)

## Independent finding

The Rust diagnostic sanitizer in `core/src/security.rs::redact_sensitive` previously detected credential-bearing HTTP header names only when their separator was a literal colon. A diagnostic containing an encoded separator (for example, `Authorization%3A` or layered `Cookie%25253A`) was not recognized as a sensitive header and could retain its value. The Android metadata sanitizer already covered these forms, making this a Rust/Android parity gap.

## Production correction and regression

The Rust header detector now recognizes a literal colon and any number of percent-encoding layers of the colon marker (`%3a`, `%253a`, `%25253a`, and further `%25` nesting). It retains the existing earliest-sensitive-header rule and folded-header handling, and redacts the value rather than trying to decode or preserve it.

Executable regression `security::tests::percent_encoded_sensitive_header_colons_never_expose_credentials` covers the five sensitive header families, mixed case, multiple encoding depths, diagnostic prefixes, and an ordinary non-header sentence. The implementation was committed directly to `master` as `161608e53826feaf41a01c972ee1babbb4e15bd8`, with the subsequent borrow-capture correction `5106ea1b11cc43562dfbfba30408298a67a2d4ee`.

## Qualification status

These commits establish source and test changes, **not** passing exact-head qualification. Require the complete eight-workflow matrix on the final current-master SHA and record its run IDs before checking RMD-1802, RMD-1803, RMD-1805 or global closeout items. Keep human YouTube/service-policy/legal release approval separate.
