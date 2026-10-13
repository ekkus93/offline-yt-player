# RMD-1802 — Library Details persisted metadata presentation review (2026-10-13)

## Finding

`app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` bounded the Library Details title and quality label, but rendered the stored provider/source identity, canonical URL, and per-asset relative path/MIME summary directly into Compose. A compromised/imported gateway record or future provider that supplied credential-bearing URL parameters could cause signed parameters to appear in the Details UI. Unbounded stored strings also lacked the display-size limit already applied to diagnostics.

## Production remediation

Apply the shared `SourceMetadataPolicy.diagnostic` presentation sanitizer to the source identity, canonical source URL, and the combined per-asset summary. This strips HTTP header credentials, redacts URL query/userinfo/fragment tokens, removes control characters, and bounds each displayed field to 512 characters without changing persisted source or asset metadata.

## Executable qualification and limitations

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt::libraryDetailsPanelRedactsPersistedUrlsAndAssetPaths` opens the production Details button, routes through an injected `AppLibraryDetailsGateway`, and asserts secret-bearing canonical/asset URLs are displayed with redacted query text and the literal marker is absent.

Do not treat the source/test change as qualification before the exact-head Android smoke lane (including the packaged Compose method) passes alongside the other seven standard workflows. This focused finding does not complete the repo-wide code review, RMD-1803 freeze/closeout, RMD-1805 definition of done, or separate human YouTube/service-policy/legal gate.
