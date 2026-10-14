# RMD-1802 — Quoted diagnostic header-name redaction (2026-10-14)

## Independent production finding

`core/src/security.rs::redact_sensitive` recognized sensitive header names followed by an immediate or whitespace-separated literal/encoded colon, but did not recognize the closing quote in a JSON/dictionary-style field name such as `{"Authorization": "Bearer SECRET"}`. Such diagnostic renderings could leave the value in the returned text. Stringified JSON with backslash-escaped quotes had the same gap. This is a potential redaction weakness, not a claim that any production provider currently emits these strings.

## Implementation and executable regression

For a sensitive field name beginning after an opening single/double quote, the matcher now optionally consumes a closing quote (or a backslash-escaped closing double quote) and any following spaces/tabs before the existing literal/percent-encoded colon matcher. It keeps ordinary explanatory text unchanged when no header separator exists. The atomic Rust change adds `security::tests::quoted_json_header_names_and_escaped_diagnostic_keys_never_leak`, covering Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-Api-Key, encoded separators, stringified JSON and ordinary text.

## Qualification

Implementation and regression are on `master`, with commit identity supplied by the Git history. Require the complete eight-workflow exact-head CI matrix, independent review, and canonical TODO reconciliation before RMD-1802/1803/1804/1805 or global closeout is checked. External YouTube/service/legal approval remains a separate human gate.
