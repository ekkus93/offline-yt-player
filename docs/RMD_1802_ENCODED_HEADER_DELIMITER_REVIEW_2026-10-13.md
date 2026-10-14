# RMD-1802 — Percent-encoded diagnostic header delimiter review (2026-10-13)

## Finding

The Android presentation boundary `SourceMetadataPolicy.redactDiagnosticHeaders` recognized standard sensitive header names only when followed by a literal colon. Provider/FFI-supplied diagnostics containing percent-encoded colons such as `Authorization%3A Bearer <private>`, `Cookie%253A session=<private>`, or `X-Api-Key%3a <private>` could be displayed in Compose, Media3 metadata, or exported recovery text without removing the secret value. The URL sanitizer does not cover standalone header values.

## Correction

Extend the existing sensitive-header delimiter recognition to literal colon or one/multiple layers of percent-encoded colon, without changing safe presentation text or provider/source data. Redact the entire header suffix, retaining the bounded field label. Host-JVM regression `SourceMetadataPolicyTest.percentEncodedHeaderSeparatorsNeverExposeCredentials` exercises title, channel, description, quality-label, and diagnostic projections with synthetic private values and multiple encodings.

## Qualification and closeout

Implementation and regression test are committed together on `master`; **this note is not evidence of a passing exact-head CI matrix**. Record the exact implementation SHA and all required CI workflow run IDs after they complete. RMD-1802 independent review, RMD-1803 candidate freeze, RMD-1804 post-merge verification, RMD-1805 engineering closeout, and the external YouTube/service-policy/legal release gate remain open until their independent acceptance conditions are met.
