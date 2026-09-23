# RMD-103 Android scheduler reconciliation

This evidence note reconciles the implementation and qualification behind RMD-103 without replacing or compressing the detailed remediation TODO. The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; its RMD-103 checkboxes must only be marked complete when this evidence is incorporated there after the evidence PR itself is qualified and merged.

## Scope

RMD-103 requires a compliant Android download-runtime scheduling abstraction by API level. User-triggered downloads must route through a legal API 34+ User-Initiated Data Transfer path and an API 26-33 fallback without duplicating the durable domain state model.

This note intentionally does not close RMD-500. The scheduler launch point and shared durable queue identity are implemented here; the long-lived durable worker loop and full pause/resume/cancel/retry/progress behavior remain owned by RMD-500 and later E2E qualification.

## Implementation evidence

- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt` introduces the production `DownloadExecutionScheduler` abstraction plus `AndroidDownloadExecutionScheduler`.
- `DownloadExecutionSchedulerPolicy.schedulerKindForSdk` selects `UserInitiatedDataTransferJob` for SDK 34+ and `ForegroundServiceFallback` for SDK 26-33.
- `app/src/main/AndroidManifest.xml` declares `android.permission.RUN_USER_INITIATED_JOBS` and the private `.downloads.DownloadUserInitiatedJobService` with `android.permission.BIND_JOB_SERVICE`.
- The API 34+ path builds a `JobInfo` for `DownloadUserInitiatedJobService`, attaches only the durable queue item id as extras, maps `DownloadNetworkPreference.WifiOnly` to `JobInfo.NETWORK_TYPE_UNMETERED`, supplies estimated network bytes when supported, and calls `setUserInitiated(true)` on SDK 34+.
- `DownloadUserInitiatedJobService` owns the UIDT notification by calling `setNotification(...)` on SDK 34+ and uses the same `offline_downloads` notification channel/id contract as the foreground fallback.
- The API 26-33 fallback starts `DownloadForegroundService` with `ACTION_SCHEDULE_WORK` and the same durable queue item id, preserving one shared durable queue/control model instead of carrying URL/title/provider payloads in platform extras.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGateway.kt` wraps the generated download-control gateway so enqueue/schedule flows call the same durable control path before scheduling platform execution.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` documents the API 34+ UIDT path, API 26-33 fallback path, notification behavior, reboot/process-death boundaries, Android 15 foreground-service restrictions, and the remaining RMD-500/RMD-1500 boundaries.

## Qualification evidence

- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionSchedulerPolicyTest.kt` verifies SDK-based scheduler selection, estimated-network-byte and UIDT flag SDK gates, API 26-33 fallback behavior, durable-queue-only job extras, deterministic job ids, network constraint mapping, manifest permission/service wiring, and use of a single durable queue contract.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/SchedulingDownloadControlGatewayTest.kt` verifies the scheduling gateway preserves enqueue results, forwards the durable queue item id, estimated bytes, and network preference to the scheduler, and reports scheduling failures without fabricating durable queue completion.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` is part of the documentation qualification for the background-execution model and preserves the distinction between RMD-103 scheduling and RMD-500 durable worker execution.
- PR #326 exact head `93ecb78670c178853173f3d83e145512e28d0d37` passed PR CI run `35827300313` and PR Android-smoke run `35827300329`.
- The same exact head also passed push CI run `35827278438` and push Android-smoke run `35827278467`.
- PR #326 merged as `cf5f5e588118273d3dd88eff42ca97994513e232`.
- Post-merge master verification passed CI run `35830038817` and Android-smoke run `35830038810` on exact merge SHA `cf5f5e588118273d3dd88eff42ca97994513e232`.

## Reconciliation result

RMD-103 has production scheduler abstraction, API 34+ UIDT job scheduling, the required API 34+ permission/service wiring, network constraints and estimated-byte handling, UIDT notification ownership, API 26-33 foreground-service fallback, one shared durable queue/control identity across both paths, scheduler-selection tests, exact-head PR CI, merged-master evidence, and post-merge master verification.

The canonical remediation TODO may therefore reconcile the RMD-103 checkboxes as complete after this evidence PR is itself qualified and merged. RMD-500, RMD-1500, and final RMD-1800 closeout remain open because the durable worker loop and full Android E2E proof are separate remediation requirements.
