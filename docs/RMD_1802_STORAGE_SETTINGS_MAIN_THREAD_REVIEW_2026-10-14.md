# RMD-1802 — Storage Settings UI-thread filesystem work (2026-10-14)

## Independent review finding

At exact `master` `9f37be0dd2c84779d96a13b89ebf760769793fae`, `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt::StorageSettingsPage` executes `StorageSettingsManager.summarize()` during initial Compose composition and after each cleanup. Its Confirm button also calls `StorageSettingsManager.cleanup()` synchronously in the click callback. These are main-thread calls, unlike the Download Setup thumbnail and Add/Download paths that already use `Dispatchers.IO`.

`app/src/main/java/com/ekkus/offlineytplayer/settings/StorageSettingsManager.kt` recursively enumerates app-private media/cache trees, performs file metadata operations, reads `StatFs`, and recursively deletes cleanup targets. A large or slow storage tree can stall UI input and cause an application-not-responding condition. The cleanup confirmation also allows duplicate actions while a cleanup is in progress, and synchronous exception handling does not protect the UI from long-running operations.

## Required correction and behavioral qualification

1. Move the initial storage summary to lifecycle-scoped asynchronous work on `Dispatchers.IO`; show an explicit loading state instead of blocking composition.
2. Move both destructive cleanup and the subsequent storage summary refresh to `Dispatchers.IO`; preserve cleanup errors, handle coroutine cancellation, and disable duplicate confirmations while cleanup is active.
3. Add a packaged Android Compose instrumentation test with an injected storage-operations fake that blocks on latches. Verify the Compose UI remains responsive while scan and cleanup are blocked, that work executes off the Android main thread, and that the final summary is refreshed. Include this test in the required API-29 smoke lane.
4. Run Android JVM/Compose instrumentation, Rust/core and full exact-head CI; reconcile this finding only after the source and tests are committed to `master` and all required workflows pass.

## Current qualification and status

The preceding exact `master` `9f37be0dd2c84779d96a13b89ebf760769793fae` passed seven of eight workflows. Android smoke run 38533085449 failed in `ProductionComposeLayoutAccessibilityTest.compact_add_and_resolved_setup_keep_primary_actions_reachable`, waiting for resolved source title; this failure is separate from the Storage finding. The failed Android smoke job was accepted for rerun on 2026-10-14; its result must be checked by run attempt. The other seven successful workflow runs do not qualify a future Storage correction.

**Open:** Source correction, device regression, exact-head qualification, RMD-1802/1803/1805 independent review, and final engineering closeout. Do not check any canonical remediation checkbox based on this finding alone. External YouTube/service/legal release approval remains separate.

## Implementation and regression update (2026-10-14)

- Source implementation commit `6625ec1781be6d5a988964024bbd98003eb47bf5` changed `StorageSettingsManager.kt`, `AppShell.kt`, and `ProductionComposeBehaviorTest.kt`. Storage scanning, cleanup, and refresh now run on `Dispatchers.IO`, with loading/busy state and disabled duplicate cleanup controls. Packaged Compose regression: `storageSettingsScanAndCleanupDoNotBlockMainThreadOrDuplicateCleanup`.
- Initial exact-head CI `38543992550` failed only in the Android JVM job because `StorageSettingsRuntimeConsumptionTest` still asserted the old source literal `manager.cleanup(pending)`; this is not a production regression and not sufficient to qualify the implementation.
- Follow-up `b0d27bb1c6f18423959d9b016e43943ceff14509` replaces the source-text assertion with a JVM filesystem behavioral regression `storageSummaryAndCleanupOperateOnRealAppOwnedFiles`. It checks real accounted categories, authorized cache/incomplete cleanup, preservation of media/DB files, and refreshed totals without Android runtime.
- **Qualification remains pending** for corrected SHA `b0d27bb1c6f18423959d9b016e43943ceff14509`. The mandatory eight-workflow matrix and API-29 packaged Compose test must pass on the final candidate exact head before any closeout checkbox can be changed. Final independent review remains open. The external YouTube/service/legal gate remains separate.

## Passing exact-head CI evidence (2026-10-14)

The corrected implementation and this review's preceding reconciliation passed all eight standard GitHub Actions workflows at exact `master` SHA `3a977b3809efeea2e916ce7aaed8009cfc666496`:

| Workflow | Run ID | Result |
| --- | --- | --- |
| Main Rust/Android CI, JVM tests, UniFFI | 38545569497 | Success |
| API-29 Android smoke, including packaged Compose regression | 38545569278 | Success |
| Android FGS timeout/UIDT | 38545569285 | Success |
| Supply chain | 38545569269 | Success |
| CI evidence | 38545569435 | Success |
| Deterministic fixture E2E | 38545569295 | Success |
| Android cold start / process death | 38545569402 | Success |
| Android real-network E2E | 38545569343 | Success |

This meets the focused Storage-settings qualification requirement at the cited exact SHA. The earlier Storage defect is corrected and its bounded-I/O regression is included in the passing Android smoke lane. The separate RMD-1802 independent-review sweep, final candidate freeze, RMD-1803/1804/1805 closeout, and human-controlled YouTube/service/legal gate remain open. A future documentation commit requires its own exact-head CI; do not transplant earlier run IDs to qualify that later commit.
