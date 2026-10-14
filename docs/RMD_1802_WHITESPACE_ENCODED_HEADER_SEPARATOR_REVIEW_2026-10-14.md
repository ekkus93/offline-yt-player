# RMD-1802 — Whitespace before encoded sensitive-header separators (2026-10-14)

## Independent production review

At exact `master` `b53911709e05830ecf613356ddecce96b8411e49`, `core/src/security.rs::redact_sensitive` recognizes sensitive HTTP diagnostic fields followed immediately by a literal colon or layered percent-encoded colon. Some upstream loggers insert spaces or tabs between a field name and the separator (for example, `Authorization %3A Bearer ...`). The previous matcher rejected that whitespace and could return the credential value verbatim. This is a potential diagnostic confidentiality leak, not a claim that a specific provider currently emits the form.

## Correction and executable evidence

The matcher now consumes only ASCII space and tab between the sensitive field name and the literal/encoded colon; the reported match length includes the consumed whitespace, so the entire value is replaced with `[REDACTED]`. It retains the existing multi-layer `%25` decoding detector, prefixed/indented header handling, folded-line handling and ordinary text preservation. New Rust regression `security::tests::whitespace_before_literal_or_encoded_header_separator_never_leaks` exercises Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-Api-Key, layered encodings, timestamp/context prefixes and folded continuations.

## Qualification and closeout

This is an implementation plus deterministic Rust regression; require Rust fmt/clippy/tests and the complete exact-head CI matrix before declaring the commit qualified. Separately diagnose Android smoke's Compose detach-lifecycle exception (run 38588134340) and the real-network emulator disappearance (run 38588134628). Neither is resolved or waived by this Rust fix. Keep RMD-1802, RMD-1803, RMD-1804, RMD-1805, global completion controls and the external YouTube/service/legal approval unchecked until their independent acceptance requirements are satisfied.
