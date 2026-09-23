# RMD-104 API 35 foreground-service timeout qualification

This note records the API 35 qualification lane added for the remaining RMD-104 shortened-timeout evidence item. It supplements, but does not replace, the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-104 already had a retained-service inventory, a production `Service.onTimeout(startId, fgsType)` implementation, source-order/JVM proof that timeout state is persisted before stopping, and packaged-app persistence smoke coverage. The remaining unchecked item was Android-side qualification using a shortened foreground-service timeout where applicable.

## Implementation

- `.github/workflows/android-fgs-timeout.yml` adds an isolated API 35 emulator lane for dataSync foreground-service timeout qualification.
- The lane uses the CI-oriented `aosp_atd` API 35 x86_64 image with an extended boot timeout so slow, no-hardware-acceleration Linux runners can reach the actual test instead of failing during emulator unlock/setup.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutAdbInstrumentedTest.kt` configures the Android 15+ test hook with `device_config put activity_manager data_sync_fgs_timeout_duration 1000`, starts the production `DownloadForegroundService`, sends the app to the background, and polls the production timeout persistence store until `Service.onTimeout(...)` records timeout state.
- The test restores the `device_config` key and stops the service after execution.

## Qualification evidence

- PR #331 exact head `eb00501cb0d657da3ed26678f589cd492818d7c9` passed PR CI run `35839348685`, PR Android-smoke run `35839348689`, and PR Android FGS-timeout run `35839348784`.
- PR #331 merged as `241ecfc65db2a58e375aee00a9fa9712e6fd3ee9`.
- Post-merge master verification on exact SHA `241ecfc65db2a58e375aee00a9fa9712e6fd3ee9` passed CI run `35846090097`, Android-smoke run `35846090151`, and Android FGS-timeout run `35846090095`.

## Reconciliation result

The remaining RMD-104 shortened-timeout qualification requirement is satisfied by the API 35 FGS-timeout lane and its post-merge master verification. The canonical remediation TODO may reconcile the remaining RMD-104 checkbox after this evidence update is itself qualified and merged.

## Boundaries

This is a focused RMD-104 platform-compliance lane. It does not close RMD-500 durable worker execution, RMD-1500 end-to-end transfer behavior, or final RMD-1800 exact-head full qualification.

## External reference

Android's foreground-service timeout documentation states that Android 15 introduces `Service.onTimeout(int, int)` for `dataSync` and `mediaProcessing` foreground services and documents the `device_config put activity_manager data_sync_fgs_timeout_duration <duration-ms>` command for testing shortened dataSync timeouts.
