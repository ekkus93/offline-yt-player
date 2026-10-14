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
