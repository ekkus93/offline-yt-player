# Android Background Execution Model

This document records the current Offline YT Player Android background-execution contract for the remediation track. It is intentionally explicit about what is implemented today and which later RMD tasks still own durable production execution.

## API 34+ user-initiated transfer path

User-requested downloads are scheduled through `AndroidDownloadExecutionScheduler` using `DownloadSchedulerKind.UserInitiatedDataTransferJob` on SDK 34 and newer.

Implementation entry points:

- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt`
- `DownloadUserInitiatedJobService`
- `android.permission.RUN_USER_INITIATED_JOBS` in `app/src/main/AndroidManifest.xml`

The scheduler attaches the durable queue item id as job extras, maps Wi-Fi-only preference to `JobInfo.NETWORK_TYPE_UNMETERED`, supplies estimated network bytes when available, calls `setUserInitiated(true)` on the API 34+ path, and owns the required transfer notification from the job service.

The current job service is the legal launch and notification owner for user-requested work. The long-lived durable worker loop remains owned by RMD-500 and must be connected to this launch point before final download-runtime closeout.

## API 26-33 fallback path

SDK 26-33 uses `DownloadSchedulerKind.ForegroundServiceFallback` from the same `DownloadExecutionScheduler` abstraction. The fallback starts `DownloadForegroundService` with `ACTION_SCHEDULE_WORK` and passes the same durable queue item id.

The fallback deliberately shares the queue/control contract with the API 34+ path rather than creating a second state model. RMD-500 owns connecting both launch mechanisms to the same production durable worker execution loop.

## Notification behavior

Download notifications use channel `offline_downloads` and notification id `4100`.

Current production behavior:

- API 33+ notification permission is requested from `MainActivity` when required.
- The grant/denial state is persisted through `DownloadNotificationPermissionStateStore`.
- Denial must not mutate durable queue state or silently report work as complete.
- In-app queue state remains authoritative when notification permission is denied.
- Notification actions for pause, resume, and cancel dispatch through `DownloadForegroundControlDispatcher` to the generated download-control gateway.

Relevant tests:

- `DownloadNotificationPermissionPolicyTest`
- `NotificationPermissionInstrumentationContractTest`
- `NotificationPermissionStateInstrumentationTest`
- `DownloadForegroundControlDispatcherTest`

## Reboot and process-death recovery

`DownloadRebootReceiver` treats boot as a durable-state signal only. It does not directly launch a `dataSync` foreground service from `BOOT_COMPLETED`, and it does not use `LOCKED_BOOT_COMPLETED` for the v1 recovery contract.

Current boot receiver invariants:

- `BOOT_COMPLETED` is the only declared boot action.
- `LOCKED_BOOT_COMPLETED` is not declared.
- The receiver does not open credential-encrypted DB/media state before unlock.
- The receiver does not call `ContextCompat.startForegroundService(...)`.
- Future legal scheduling is delegated to the durable scheduler/reconciliation work owned by RMD-103/RMD-500/RMD-1200.

Relevant tests:

- `DownloadBootRecoveryPolicyTest`
- `DownloadRebootRecoveryPolicyTest`

Process-death recovery is not closed by this document. Startup reconciliation and interrupted transfer recovery remain owned by RMD-1200, with durable worker repair semantics owned by RMD-500.

## Android 15 foreground-service restrictions

The app must not start a `dataSync` foreground service directly from boot on target SDK 35+. The current receiver enforces that by performing no foreground-service launch from boot.

The retained `DownloadForegroundService` implements `Service.onTimeout(startId, fgsType)`, persists timeout metadata through `DownloadForegroundTimeoutStore`, removes the foreground notification, and stops the timed-out service instance. This prevents Android 15+ foreground-service timeout handling from devolving into an unhandled fatal service timeout path.

Relevant test:

- `DownloadForegroundTimeoutPolicyTest`

## Known limits still owned by remediation tasks

This document does not claim final engineering closeout for background execution. The following work remains required before final release qualification:

- RMD-500: connect scheduler launch points to the real durable worker loop.
- RMD-501/RMD-502: make durable queue state and worker claims the source of truth.
- RMD-503 through RMD-507: complete pause/resume/cancel/retry/progress behavior against the durable worker.
- RMD-508: connect network-change observation to the long-lived production execution owner.
- RMD-1200: invoke startup reconciliation and prove process-death/reboot recovery end to end.
- RMD-1500: qualify fixture E2E, connectivity E2E, and notification-control E2E on Android instrumentation infrastructure.
