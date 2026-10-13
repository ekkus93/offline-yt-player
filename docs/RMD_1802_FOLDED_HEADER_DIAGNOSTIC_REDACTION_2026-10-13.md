# RMD-1802 — Folded sensitive-header diagnostic redaction (2026-10-13)

## Independent finding

`core/src/security.rs::redact_sensitive` removed credential-bearing `Authorization`, `Cookie`, and API-key header values from the line containing the header name, but emitted subsequent indented continuation lines verbatim. A diagnostic with `Authorization: Bearer` followed by a folded line containing a token could therefore disclose the token even though the header was recognized. This is a production diagnostic-sanitization defect, not a policy-only test gap.

## Correction and executable evidence

Track a sensitive header across contiguous indented continuation lines, replacing each with a fixed marker. Reset this state on the next non-indented line so ordinary diagnostic context remains visible. The Rust regression `folded_sensitive_header_values_never_leak_from_diagnostics` covers multiple folded authorization lines, a later cookie continuation, and ordinary intervening context.

## Qualification and closeout status

Implementation and regression are committed directly to `master` together with this review record. **Exact-head CI and complete independent review remain required.** This finding does not close RMD-1802, RMD-1803, RMD-1805, global proof requirements, or the external YouTube/service-policy/legal gate. Record exact SHA and passing workflow IDs before final reconciliation.
