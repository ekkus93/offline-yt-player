# RMD-1802 — Library rename success-result display sanitization (2026-10-13)

## Finding

`ui/LibraryDownloads.kt` already used `SourceMetadataPolicy.diagnostic` for Library rename errors, but a successful `AppLibraryMutationGateway.renameDisplayTitle` returned String was interpolated unmodified into `Renamed to ...` on-screen status. This is an independent response boundary: a malformed gateway result, future provider-derived title, or imported durable title containing an HTTP signed query/credential could be displayed without redaction or character limits.

## Production correction

The success presentation now calls `SourceMetadataPolicy.title` on the returned title. The string sanitizer limits title length, redacts credential-bearing URL query/userinfo/fragment and HTTP header text, and normalizes controls. No persistent rename payload is changed. Existing normal rename and removal behaviors remain unchanged.

## Behavioral coverage

`ProductionComposeBehaviorTest.libraryRenameSuccessDoesNotExposeUntrustedReturnedTitle` invokes the actual rendered Rename and Save rename callbacks, injects a successful gateway result with a synthetic signed URL, waits for the sanitized success message, and asserts the secret marker is absent. The original mutation-routing Compose test continues to verify actual ID/title and confirmation arguments.

This is source/test evidence, not qualified closure. A complete eight-workflow exact-head CI matrix, independent cross-language production review, final RMD-1803 freeze and RMD-1804/1805 reconciliation are still required; separate human YouTube/service-policy/legal release approval is unchanged.
