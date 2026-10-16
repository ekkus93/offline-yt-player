# RMD-104 API 35 foreground-service timeout qualification

> **Status correction (2026-10-16):** This historical note overstates the current lane. The current
> `DownloadForegroundTimeoutAdbInstrumentedTest` does not set `device_config`, start the production
> service, or observe `Service.onTimeout`; it directly calls the persistence store. Treat the claims
> below as historical intent, not current evidence for a forced Android timeout. The canonical
> checklist subtask is reopened pending a real service callback qualification.

This note records the API 35 qualification lane added for the remaining RMD-104 shortened-timeout evidence item. It supplements, but does not replace, the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-104 already had a retained-service inventory, a production `Service.onTimeout(startId, fgsType)` implementation, source-order/JVM proof that timeout state is persisted before stopping, and packaged-app persistence smoke coverage. The remaining unchecked item was Android-side qualification using a shortened foreground-service timeout where applicable.

## Implementation

- `.github/workflows/android-fgs-timeout.yml` adds an isolated API 35 emulator lane for dataSync foreground-service timeout qualification.
- The lane uses the CI-oriented `aosp_atd` API 35 x86_64 image with an extended boot timeout so slow, no-hardware-acceleration Linux runners can reach the actual test instead of failing during emulator unlock/setup.
- `DownloadForegroundTimeoutAdbInstrumentedTest` checks that the packaged manifest retains the `dataSync` service and directly writes/reads the timeout persistence store. It does not configure `device_config`, start the service, or poll for `Service.onTimeout`.
- The workflow runs on API 35 and checks the persistence store contract, but it does not qualify a shortened platform timeout or the service callback.

## Qualification evidence

- Historical runs on PR #331 (`eb00501cb0d657da3ed26678f589cd492818d7c9`) and merge `241ecfc65db2a58e375aee00a9fa9712e6fd3ee9` passed the then-configured tests, including Android FGS-timeout runs `35839348784` and `35846090095`. Those test results do not establish that `Service.onTimeout` was invoked.

## Reconciliation result

The RMD-104 shortened-timeout qualification requirement remains open. The API 35 lane and its historical runs prove only the packaged persistence-store check, not a shortened timeout or production callback. The canonical remediation TODO has been reopened accordingly.

## Boundaries

This is a focused RMD-104 platform-compliance lane. It does not close RMD-500 durable worker execution, RMD-1500 end-to-end transfer behavior, or final RMD-1800 exact-head full qualification.

## External reference

Android's foreground-service timeout documentation states that Android 15 introduces `Service.onTimeout(int, int)` for `dataSync` and `mediaProcessing` foreground services and documents the `device_config put activity_manager data_sync_fgs_timeout_duration <duration-ms>` command for testing shortened dataSync timeouts.
