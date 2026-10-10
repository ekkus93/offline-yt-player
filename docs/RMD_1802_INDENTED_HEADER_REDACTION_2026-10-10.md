# RMD-1802 — Indented sensitive-header diagnostic redaction (2026-10-10)

Independent Rust security review found that `core/src/security.rs::redact_sensitive` only recognized sensitive HTTP header names at column zero. Indented diagnostic lines such as `  Authorization: Bearer ...`, `\tCookie: ...`, and `  X-Api-Key: ...` would pass through with secret values intact. The fix recognizes header names after leading whitespace while preserving the existing redaction output and a deterministic regression test.

This finding remains subject to exact-head Rust tests and the required CI matrix. It does not imply RMD-1802 or final engineering closeout is complete.
