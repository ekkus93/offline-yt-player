# RMD-1802 — Android presentation diagnostic-header separator review

Date: 2026-10-14
Status: implementation/testing proposed on master; final exact-head qualification and RMD-1800 closeout are open.

## Production finding

`app/src/main/java/com/ekkus/offlineytplayer/coregateway/SourceMetadataPolicy.kt` is the Android presentation boundary for titles, channels, descriptions, quality labels and diagnostics, independently of the Rust redactor. Its previous sensitive-header matcher supported ASCII whitespace before literal colon and some encoded colons. It could leave values visible for an equals separator, encoded equals, a quoted header name, or Unicode-space padding. Folded continuation detection was also ASCII-indentation-only. This issue is separate from, but related to, the Rust Unicode-whitespace finding in `docs/RMD_1802_UNICODE_WHITESPACE_DIAGNOSTIC_REDACTION_2026-10-14.md`.

## Production change

Extend the header matcher to Unicode whitespace/spacing, optional escaped closing quotes and literal/percent-encoded colon or equals separators, and recognize Unicode-indented header continuation lines. Keep redaction bounded before the UI renders metadata.

## Proof

`app/src/test/java/com/ekkus/offlineytplayer/coregateway/SourceMetadataPolicyTest.kt::quotedEqualsAndUnicodeSpacedHeaderFieldsNeverExposeCredentials` exercises all five header families across presentation entry points, quoted/equals/encoded forms and indented continuation lines with synthetic marker values.

- [ ] Confirm Android host-JVM tests pass on the final exact master SHA.
- [ ] Confirm all eight exact-head CI/Android/supply-chain lanes pass.
- [ ] Finish independent Rust/Android production review and reconcile RMD-1802/1803/1804/1805 and global controls with evidence, not checkbox assertion.
- [ ] Preserve external YouTube/service-policy/legal release approval as a separate unresolved gate.
