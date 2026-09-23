# RMD-102 Boot recovery reconciliation

This evidence note reconciles the implementation and qualification behind RMD-102 without replacing or compressing the detailed remediation TODO. The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; its RMD-102 checkboxes must only be marked complete when this evidence is incorporated there.

## Scope

RMD-102 requires replacing illegal or fragile boot-start behavior with platform-compliant durable recovery. Boot handling must not directly start a `dataSync` foreground service on target SDK 35+, must not access credential-protected storage before unlock, and must make interrupted work recoverable through a legal future scheduling/reconciliation path.

## Implementation evidence

- `app/src/main/AndroidManifest.xml` declares `android.permission.RECEIVE_BOOT_COMPLETED` and a private `.downloads.DownloadRebootReceiver` for `android.intent.action.BOOT_COMPLETED`.
- The manifest does not declare `android.intent.action.LOCKED_BOOT_COMPLETED`, so no Direct Boot pre-unlock path is required for v1.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadRebootReceiver.kt` delegates boot handling to `DownloadBootRecovery.onReceive(intent?.action)` and does not call `startService`, `ContextCompat.startForegroundService`, `DownloadForegroundService`, or `AndroidDownloadExecutionScheduler` from the boot receiver path.
- `DownloadBootRecovery` models `BOOT_COMPLETED` as `DeferUntilAppStartup`, keeps `StartsForegroundServiceFromBoot = false`, keeps `UsesLockedBootCompleted = false`, keeps `OpensCredentialProtectedStorageOnBoot = false`, and points recovery to `MainActivity.bootstrapProductionUi -> GeneratedUniffiCoreGateway.reconcileStartup`.

## Qualification evidence

- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadRebootRecoveryPolicyTest.kt` asserts the manifest exposes only the private unlocked boot receiver, excludes `LOCKED_BOOT_COMPLETED`, and proves the receiver does not start a foreground service, touch protected state, or schedule work directly from boot.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadBootRecoveryPolicyTest.kt` asserts `BOOT_COMPLETED` defers to startup reconciliation, does not start a foreground service, does not open credential-protected storage, preserves recoverability, and keeps scheduling legal.
- Current merged master `4a36792f866bc596b7470d5f0328205d75c7f898` inherits the earlier qualified boot-recovery implementation and has post-merge RMD-101 evidence pending standard CI run `35822366166` and Android-smoke run `35822366114`.

## Reconciliation result

RMD-102 has production boot handling, manifest-level proof, JVM policy coverage, no `LOCKED_BOOT_COMPLETED` path, no boot-time foreground-service launch, no pre-unlock credential-protected storage access, and a documented durable startup reconciliation path. The canonical remediation TODO may therefore reconcile the RMD-102 checkboxes as complete after this evidence PR is qualified and merged, while preserving RMD-103 and later runtime-scheduler work as separate open items.
