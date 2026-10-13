# RMD-1802 — Android folded credential-header redaction parity (2026-10-13)

## Independent disclosure finding

The Android `SourceMetadataPolicy.redactDiagnosticHeaders` sanitized a sensitive HTTP header's own line but forgot that `Authorization: Bearer`, `Cookie:`, or `X-Api-Key:` values may continue on subsequent lines beginning with SP or HTAB, lacking another header name. Its previous per-line `lineSequence().joinToString` processing treated those continuation bytes as ordinary text and rendered them verbatim through all five public UI/diagnostic projections. This is a credential-disclosure risk in upstream diagnostic/error text. The Rust diagnostic redactor had separately acquired folded-header protection in `core/src/security.rs` on the concurrent master.

## Correction and executable proof

Production Android now maintains redaction state between lines, replaces unnamed SP/HTAB continuations with `[REDACTED_HEADER_CONTINUATION]`, resets when ordinary non-indented text resumes, and still recognizes a separate indented named header. No credential text is retained in the marker. The fast-JVM regression `SourceMetadataPolicyTest.foldedSensitiveHeaderLinesNeverDiscloseRawValues` exercises title, channel, description, quality, and diagnostic; guarantees a synthetic secret cannot reach output; preserves intervening safe context; and verifies separately indented `Cookie:` still gets its named redacted representation. The existing prefixed/indented-header regression remains intact.

## Qualification and closeout

This is production code and deterministic host-JVM proof, **not** complete RMD-1802/RMD-1803 qualification until all required workflows pass on this new exact `master` SHA. RMD-1804 post-merge verification, RMD-1805 engineering definition of done, global proof rules, and the separate human YouTube/service-policy/legal gate remain open.
