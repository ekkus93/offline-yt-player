# RMD-1802 — malformed diagnostic URL redaction finding (2026-10-10)

Independent Rust review of `core/src/security.rs::redact_url_token` at `936e265a814e2435d8a70a1f8be5f9f825380a2b` found a fail-open branch: if `Url::parse(url_text)` rejects a token containing an HTTP(S) URL, the original unredacted token is returned. A malformed URL can contain an embedded credential or signed parameter. A second HTTP(S) URL concatenated into the path of a parseable first URL can also preserve credentials belonging to the second URL.

Required correction: replace unparseable URL-bearing tokens with a bounded redaction marker rather than returning raw input, and fail closed on concatenated URLs inside one token. Add deterministic tests with synthetic markers for malformed credential URLs and concatenated URL paths; assert the markers never appear in the diagnostic. This is a new open RMD-1802 security finding, not completion evidence. Keep RMD-1802, RMD-1803, and final closeout unchecked until implementation and exact-head CI qualify the correction.

The production source-file correction was attempted but rejected by the Ralph Bridge tool safety gate in this run. No production fix is claimed.
