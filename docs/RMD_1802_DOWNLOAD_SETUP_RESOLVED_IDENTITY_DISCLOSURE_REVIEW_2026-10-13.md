# RMD-1802 — Download Setup resolved identity disclosure review (2026-10-13)

## Finding

Production `DownloadSetupPreview` in `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` rendered the provider-resolved `setup.sourceUrl` directly as a canonical URL, and displayed provider/media identity without sanitization. Resolved external source URLs may carry signed query parameters; successful Analyze/Setup output was not protected by existing error-diagnostic redaction.

## Correction

The Compose preview now renders source identity and canonical URL through `SourceMetadataPolicy.diagnostic`, and bounds the displayed title. The original URL remains unchanged in `DownloadSetupState` and is still passed to the actual enqueue gateway; only displayed text is sanitized.

The packaged `ProductionComposeBehaviorTest.downloadSetupDoesNotRenderSignedResolvedSourceUrlsOrIdentityTokens` drives Add → Analyze → Download Setup with a fake source gateway returning a signed canonical URL and a signed URL in media identity. It asserts redacted rendered strings and absence of a synthetic private marker.

## Qualification

Implementation commits `74c2d2631bc45d80fa8a8699996fa4b62b3285c5` and `8048b05219ac6e852dee76a2b2bed050a7d15f11`; instrumentation regression `ba6c0014b423d71e1cb71e44ba2ad676a45d9bab`. The full exact-head CI matrix and Android smoke qualification remain to be recorded. This focused finding does not close the independent RMD-1802 audit, final RMD-1803/1805 qualification, global proof rules or external service-policy/legal approval.
