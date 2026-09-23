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
- This PR adds `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutInstrumentedTest.kt`, proving on an installed packaged app that the timeout persistence store records start id, foreground-service type, and repeated timeout count in app-private storage.
- This PR expands `.github/workflows/android-smoke.yml` so the small API-29 smoke lane runs the packaged-app timeout persistence test alongside the existing instrumentation health, packaged UniFFI, network, and notification-permission smoke tests. API 29 cannot force Android 15's platform timeout callback, so callback-level shortened-timeout ADB qualification remains part of the broader Android qualification work when an API 35+ timeout-capable lane is introduced.

## Reconciliation result

RMD-104 has a retained-service inventory, a production `onTimeout` implementation for the remaining `dataSync` service, persistence before service stop, JVM policy/source-order coverage, and packaged-app persistence smoke coverage. The canonical remediation TODO may reconcile the inventory, `onTimeout`, and persistence subtasks after this evidence PR is itself qualified and merged. The shortened-timeout API 35+/ADB qualification expectation remains a later Android qualification/evidence item unless the current fast smoke lane is extended to support it.
