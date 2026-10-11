# RMD-1802 — Foreground-service timeout persistence review (2026-10-11)

## Independent finding

The Android 15+ dataSync timeout handler in `DownloadForegroundService.onTimeout` persists a resumable timeout record and then immediately calls `stopForeground` and `stopSelf`. The previous `DownloadForegroundTimeoutStore.persistTimeout` used `SharedPreferences.Editor.apply()`, which updates the process-local cache immediately but writes the backing XML asynchronously. A process termination following the service stop could occur before that write is durable. Existing instrumentation checked the in-memory preferences, not the disk.

## Correction and qualification

Use synchronous `SharedPreferences.Editor.commit()` before returning to the timeout handler. The record is small, and this path is an exceptional timeout callback rather than a hot loop. Keep the handler's required stop sequence unchanged. Extend `DownloadForegroundTimeoutInstrumentedTest` with `timeoutStateIsOnDiskBeforePersistReturns`, which reads the actual app-private XML immediately after the store returns, in addition to the existing value/count assertions.

Production path: `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt`.
Behavioral test: `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutInstrumentedTest.kt`.
API-35 end-to-end gate: `.github/workflows/android-fgs-timeout.yml`.

## Status

Implementation and test are submitted together. Exact implementation SHA and full CI run IDs must be added after the commit and required gates pass. RMD-1802, RMD-1803, RMD-1805, and global closeout remain unchecked. The external service-policy/legal release gate is separate.
