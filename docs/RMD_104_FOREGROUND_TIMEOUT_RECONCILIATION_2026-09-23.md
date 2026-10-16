# RMD-104 foreground-service timeout reconciliation

> **Status correction (2026-10-16):** The earlier API-35 workflow did not perform the shortened
> timeout procedure described in this historical reconciliation. The current candidate now adds an
> instrumented real-service timeout test and sets a 10-second device timeout in its driver. The test
> passed locally on API 36; exact-head API-35 CI remains pending, so the canonical TODO checkbox stays
> open until that run passes.

This evidence note records the current RMD-104 implementation and qualification evidence without replacing or compressing the detailed remediation TODO. The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; its RMD-104 checkboxes must only be marked complete when this evidence is incorporated there after the evidence PR itself is qualified and merged.

## Scope

RMD-104 requires inventorying retained foreground services, implementing `Service.onTimeout(...)` for any Android 15+ time-limited path that remains, persisting resumable/recovery state before stopping, and adding Android-side qualification where applicable.

## Implementation evidence

- `app/src/main/AndroidManifest.xml` retains one download `dataSync` foreground service, `.downloads.DownloadForegroundService`, and one media playback foreground service, `.playback.PlaybackSessionService`; there is no retained media-processing foreground service.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` records this inventory in `DownloadForegroundServiceInventory`.
- `DownloadForegroundService.onTimeout(startId, fgsType)` calls `DownloadForegroundTimeoutStore.persistTimeout(...)` before `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf(startId)`.
- `DownloadForegroundTimeoutStore` persists the last timeout start id, foreground-service type, and timeout count in app-private storage. The durable queue itself remains the source of resumable download truth under RMD-500; this timeout record is the Android service-level recovery breadcrumb that survives the service stop path.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` documents the retained `dataSync` service, Android 15 foreground-service restriction boundary, and remaining RMD-500 durable-worker responsibilities.

## Qualification evidence

- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundServiceTimeoutPolicyTest.kt` verifies the retained-service inventory, manifest service declarations, and source-order invariant that timeout state is persisted before foreground removal and service stop.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutPolicyTest.kt` covers the foreground-timeout policy surface used by the Android integration layer.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutInstrumentedTest.kt` proves on an installed packaged app that the timeout persistence store records start id, foreground-service type, and repeated timeout count in app-private storage.
- `.github/workflows/android-fgs-timeout.yml` runs packaged instrumentation on an API 35 emulator. Its current candidate driver sets `data_sync_fgs_timeout_duration` to 10 seconds and restores the prior setting. `DownloadForegroundTimeoutAdbInstrumentedTest.api35ShortenedTimeoutInvokesProductionServiceCallbackAndStopsCleanly` starts the installed production service, backgrounds the app, verifies the actual callback persisted the service start ID and `dataSync` type, then waits for shutdown. A local API 36 run passed; exact-head API-35 CI is still required.
- Historical post-merge master `1386c97531e4c863bc64b0069123b72e02370904` passed Android FGS-timeout run `35856385594`, CI `35856385367`, and Android smoke `35856385344`. The Android run's old workflow/job labels said “timeout qualification,” but the test at that head exercised the persistence store rather than the production callback.

## Reconciliation result

RMD-104 has implementation evidence for service inventory, a production `onTimeout` handler, and persistence before the service stop sequence. The candidate now adds a real shortened-timeout callback test; keep the dedicated emulator/ADB subtask **open** until exact-head API-35 CI verifies it. The test starts no active download, so this qualification does not independently prove native worker-stop ordering during active transfer.
