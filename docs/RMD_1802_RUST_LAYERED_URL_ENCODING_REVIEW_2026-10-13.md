# RMD-1802 — Layered URL encoding review (2026-10-13)

A current-master independent review of `core/src/security.rs::redact_url_token` identified an unqualified diagnostic-redaction case: doubly percent-encoded HTTP(S) schemes and encoded query delimiters are not consistently detected. A signed URL in such a form may leave its query value in an exported diagnostic.

Required correction: reject or redact encoded scheme markers and layered percent encodings at the Rust diagnostic presentation boundary. Add deterministic Rust regression coverage for doubly encoded whole URLs, path query delimiters, and mixed encoded/literal separators. Verify all eight workflows on the exact implementation head.

No closeout checkbox should be marked complete on this note alone. Keep RMD-1802/1803/1805 and the separate external policy/legal gate open.
