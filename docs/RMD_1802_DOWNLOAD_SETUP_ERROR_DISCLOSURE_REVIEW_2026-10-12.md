# RMD-1802 — Download Setup error-presentation audit (2026-10-12)

## Newly discovered unresolved finding

The production Download Setup button in `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` invokes `AppDownloadControlGateway.enqueue` and, when the gateway returns an error, currently publishes `it.message` directly as Compose status text. This path bypasses the shared `SourceMetadataPolicy.diagnostic` sanitizer used by other Android error presentations. A gateway diagnostic containing sensitive HTTP headers, signed URL parameters, or unbounded control characters could therefore be rendered directly.

## Required remediation

Import `SourceMetadataPolicy` in `AppShell.kt` and replace the direct error-message assignment in the Download Setup enqueue failure branch with `SourceMetadataPolicy.diagnostic(it.message)`. Add a packaged `ProductionComposeBehaviorTest` that drives Add → Analyze → Download Setup → Download using the real Compose button and an injected gateway failure, then verifies that the visible error is bounded and redacted and does not contain an injected synthetic marker. The production button/gateway route, not merely a policy object, must be exercised.

An additional presentation-hardening candidate is `libraryDetailsSummary` in `ui/LibraryDownloads.kt`, which interpolates `details.qualityLabel` directly despite using `SourceMetadataPolicy.qualityLabel` elsewhere. Verify its upstream bounds and add a direct bound if needed.

## Qualification

The finding is **OPEN**. No production correction is claimed by this report. RMD-1802/1803/1805 and global proof requirements must remain unchecked. After correction, require the complete exact-head CI matrix including host JVM/core, UniFFI ABI packaging, packaged Compose instrumentation and golden tests, deterministic fixture, cold start, real-network E2E, FGS timeout, supply chain, and CI evidence. External service-policy/legal approval remains separate.
