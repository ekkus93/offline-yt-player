# RMD-1802 — Android library error diagnostic header disclosure (2026-10-12)

## Finding

The actual library rename, remove and details action handlers in `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` use `SourceMetadataPolicy.diagnostic(error.message)` for visible error state. The shared Android formatter previously sanitized and bounded URL-containing diagnostics but passed through credential-bearing HTTP headers such as `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` and `X-Api-Key`. Provider/FFI diagnostic text may contain sensitive credentials even where it does not contain a URL.

## Implementation

`app/src/main/java/com/ekkus/offlineytplayer/coregateway/SourceMetadataPolicy.kt` now examines each error line before the bounded message sanitizer. It locates case-insensitive sensitive header names even after a diagnostic timestamp/prefix. The preserved prefix is URL-redacted; the entire remainder from the header value is replaced by `[REDACTED]`. If no header exists, existing URL redaction is unchanged. This avoids displaying untrusted credential values while retaining ordinary non-sensitive diagnostics.

`app/src/test/java/com/ekkus/offlineytplayer/coregateway/SourceMetadataPolicyTest.kt` adds `diagnosticRedactsCredentialHeadersEvenWhenPrefixedOrIndented` and `diagnosticRedactsSignedUrlBeforeSensitiveHeaderOnSameLine`, in addition to prior URL redaction and bounded presentation tests. Tests are host JVM proof, not emulator-dependent declarations.

## Qualification

This document and checked-in tests alone do not close the finding. Require passing Rust/Android/UniFFI, smoke/Compose/bitmap golden/accessibility, API-35 foreground timeout, deterministic fixture E2E, Android process death/cold start, real-network E2E, and supply-chain/evidence workflows for the exact resulting master SHA; review any failures. The independent repo-wide production audit, RMD-1803/1804/1805, and external YouTube/service-policy/legal approval remain **open**.
