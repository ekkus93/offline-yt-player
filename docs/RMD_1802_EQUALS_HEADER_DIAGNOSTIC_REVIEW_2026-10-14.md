# RMD-1802 — Equals-Separated Sensitive Header Diagnostic Review (2026-10-14)

## Finding

The production Rust diagnostic redactor in `core/src/security.rs::redact_sensitive` recognized sensitive HTTP field names followed by literal `:` or percent-encoded `%3A`, but did not recognize the common diagnostic key/value form `Authorization=Bearer …`, optionally separated by whitespace, or percent-encoded `%3D` and `%253D`. Consequently a sensitive header rendered with `=` could pass through the URL-only fallback unchanged and expose a credential. This is a diagnostic-disclosure defect, not a claim that HTTP header parsing itself accepted these separators.

## Production correction and deterministic regression

The existing bounded field matcher now accepts literal `=` and layered percent-encoded `3D` in addition to the original colon forms, preserving the same fail-closed redaction of the remaining line. Regression `security::tests::equals_separated_sensitive_headers_never_leak` covers Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-Api-Key, quoted key/value rendering, whitespace and encoded separators. Existing colon and URL redaction tests remain required.

## Qualification and closeout

Source and regression are committed directly to `master` under the existing master-only workflow. The exact commit SHA and eight-workflow CI outcomes must be recorded only after the runs finish and pass. Do not mark RMD-1802, RMD-1803, RMD-1804, RMD-1805 or global proof rules complete on source presence alone. Independent Rust/Android review continues; external YouTube/service-policy/legal approval remains separate.
