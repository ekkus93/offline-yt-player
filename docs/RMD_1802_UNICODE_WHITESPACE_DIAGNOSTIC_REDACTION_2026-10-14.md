# RMD-1802: Unicode whitespace diagnostic-header redaction review

Date: 2026-10-14
Scope: `core/src/security.rs::redact_sensitive`
Status: implementation committed for exact-head CI qualification; independent production review and final RMD-1802/RMD-1803/1805 closeout remain open.

## Finding

A diagnostic header field could contain Unicode whitespace (e.g., EM SPACE, NO-BREAK SPACE, NARROW NO-BREAK SPACE) before `:`, `=`, or a layered percent-encoded separator. Prior matching stripped only ASCII SPACE and TAB, so it could miss sensitive headers and leave the header value visible in diagnostics. Similarly, continuation lines indented with Unicode whitespace could escape the folded-header redaction branch.

## Fix

Use Rust `char::is_whitespace` for padding before a sensitive-header separator and after quoted field names, while preserving the exact UTF-8 byte offset for slicing the original input. Detect Unicode-indented folded continuation lines. This is a narrowly scoped diagnostic protection change; it does not weaken URL redaction or release-policy controls.

## Behavioral regression

`core/src/security.rs` adds deterministic Rust tests for all five sensitive header families and their literal, equals, quoted, prefixed, and encoded-separator variants. A second regression ensures Unicode-indented continuation lines are redacted rather than echoed. Both tests use synthetic, non-sensitive sentinel values.

## Acceptance

- [ ] Confirm regression tests pass as part of exact-head Rust CI for the implementation SHA.
- [ ] Confirm the full required exact-head CI matrix passes, including Android smoke, FGS timeout, cold start, deterministic E2E, and supply-chain gates.
- [ ] Complete independent review for Rust and Android production paths and reconcile RMD-1802/1803/1805/global checklist using precise verified evidence.
- [ ] Keep the external YouTube/service-policy/legal approval independent and unresolved.

A passing CI run for an *older* SHA is not evidence for this correction.
