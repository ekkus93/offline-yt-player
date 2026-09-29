# RMD-1106 About Metadata Reconciliation — 2026-09-29

## Scope

RMD-1106 requires the About surface to stop presenting a hard-coded `0.1.0`, expose real version/build metadata, show source revision when available, and render licenses/privacy/legal/support diagnostics only where the app actually supports them.

## Production implementation evidence

- `app/build.gradle.kts` defines runtime-backed `BuildConfig.SOURCE_REVISION` from `GITHUB_SHA`, `VERSION_NAME` from `OYP_VERSION_NAME` with a local-development default, and `VERSION_CODE` from `OYP_VERSION_CODE`.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AboutMetadata.kt` reads `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`, and `BuildConfig.SOURCE_REVISION` rather than hard-coding the displayed application version.
- `AboutMetadataProvider.revisionLabel(...)` renders a short source revision when available and a truthful local-build fallback otherwise.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AboutSettingsPage.kt` renders the runtime-backed version/build, source revision, license, privacy, diagnostics, and support entries through the Settings → About surface.

## Behavioral qualification

- `app/src/test/java/com/ekkus/offlineytplayer/ui/AboutMetadataTest.kt` verifies version-label formatting from runtime metadata, source-revision truncation, local-build fallback, and presence of user-visible support/privacy/diagnostics/license text.

## Boundary

This closes the focused RMD-1106 About metadata/runtime-wiring obligation only. It does not close the broader durable-settings tasks in RMD-1101 through RMD-1105, nor final RMD-1800 closeout.
