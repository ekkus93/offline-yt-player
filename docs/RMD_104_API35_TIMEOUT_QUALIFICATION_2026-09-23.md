# RMD-104 API 35 foreground-service timeout qualification

> **Status correction (2026-10-16):** The earlier lane described below did not force the production
> callback. The current candidate adds `api35ShortenedTimeoutInvokesProductionServiceCallbackAndStopsCleanly`
> and configures a 10-second `data_sync_fgs_timeout_duration` in the API-35 driver. That test passed
> locally on API 36; exact-head API-35 CI is still pending, so the canonical checklist subtask remains
> open until the new workflow passes on its final candidate SHA.

This note records the API 35 qualification lane added for the remaining RMD-104 shortened-timeout evidence item. It supplements, but does not replace, the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-104 already had a retained-service inventory, a production `Service.onTimeout(startId, fgsType)` implementation, source-order/JVM proof that timeout state is persisted before stopping, and packaged-app persistence smoke coverage. The remaining unchecked item was Android-side qualification using a shortened foreground-service timeout where applicable.

## Implementation

- `.github/workflows/android-fgs-timeout.yml` adds an isolated API 35 emulator lane for dataSync foreground-service timeout qualification.
- The lane uses the CI-oriented `aosp_atd` API 35 x86_64 image with an extended boot timeout so slow, no-hardware-acceleration Linux runners can reach the actual test instead of failing during emulator unlock/setup.
- `DownloadForegroundTimeoutAdbInstrumentedTest` checks the packaged `dataSync` declaration and starts the real service under a shortened platform timeout, verifies the persisted service start ID/type written by `Service.onTimeout`, and waits for the service to stop.
- `scripts/run_android_fgs_uidt_qualification.sh` saves the existing `data_sync_fgs_timeout_duration`, sets it to 10 seconds for the API-35 test, then restores or deletes the override on exit.
- Local API-36 instrumentation passed with the shortened timeout. The exact-head API-35 CI result is required before this evidence closes RMD-104.

## Qualification evidence

- Historical runs on PR #331 (`eb00501cb0d657da3ed26678f589cd492818d7c9`) and merge `241ecfc65db2a58e375aee00a9fa9712e6fd3ee9` passed the then-configured tests, including Android FGS-timeout runs `35839348784` and `35846090095`. Those test results do not establish that `Service.onTimeout` was invoked.

## Reconciliation result

The earlier workflow and its historical runs prove only the packaged persistence-store check. The candidate's new test directly exercises the production callback, but exact-head API-35 qualification remains required before closing the canonical TODO subtask.

## Boundaries

This is a focused RMD-104 platform-compliance lane. It does not close RMD-500 durable worker execution, RMD-1500 end-to-end transfer behavior, or final RMD-1800 exact-head full qualification.

## External reference

Android's foreground-service timeout documentation states that Android 15 introduces `Service.onTimeout(int, int)` for `dataSync` and `mediaProcessing` foreground services and documents the `device_config put activity_manager data_sync_fgs_timeout_duration <duration-ms>` command for testing shortened dataSync timeouts.
