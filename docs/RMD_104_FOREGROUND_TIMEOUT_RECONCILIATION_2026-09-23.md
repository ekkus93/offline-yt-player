# RMD-104 foreground-service timeout reconciliation

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
- `.github/workflows/android-fgs-timeout.yml` provides the dedicated API 35 timeout lane. It runs the packaged app on an API 35 emulator, shortens the `dataSync` foreground-service timeout through ADB/device-config, exercises the real retained service timeout path, and preserves qualification evidence artifacts.
- Exact post-merge master `1386c97531e4c863bc64b0069123b72e02370904` passed Android FGS-timeout run `35856385594`. Its `API 35 dataSync timeout qualification` job completed successfully, including the `Run API 35 dataSync timeout qualification` and `Upload timeout qualification evidence` steps. The same exact master also passed CI `35856385367` and Android smoke `35856385344`.

## Reconciliation result

RMD-104 now has evidence for all four canonical subtasks: retained-service inventory, production `onTimeout` handling, persistence before service stop, and dedicated API 35 shortened-timeout emulator/ADB qualification. After this evidence update itself is qualified and merged, the remaining canonical RMD-104 checkbox can be reconciled as complete with exact-head evidence; final global/full-matrix closeout remains governed by RMD-1800.