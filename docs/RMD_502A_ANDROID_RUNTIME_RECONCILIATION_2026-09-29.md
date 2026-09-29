# RMD-502a Android runtime reconciliation — 2026-09-29

## Scope

RMD-502a required Android runtime launch points to attach to executable durable work instead of only owning notification/lifecycle state. The closure requirement was:

- persist enough provider-neutral download-plan data for a queued job to be reconstructed after process death;
- make both API 34+ `DownloadUserInitiatedJobService` and API 26–33 `DownloadForegroundService` invoke the real core worker loop;
- add deterministic Android qualification proving a scheduled fixture job advances through the runtime into completed durable Library state.

## Implementation evidence

Current `master` includes:

- durable executable work storage in `core/src/durable_work.rs`;
- generated-UniFFI worker execution in `core/src/ffi_download_worker.rs`;
- scheduling-time executable plan persistence for supported production source URLs through `core/src/ffi_download_control.rs` and `core/src/ffi_download_enqueue_work.rs`;
- Android UIDT execution attachment in `DownloadUserInitiatedJobService.onStartJob()`;
- Android API 26–33 foreground-service execution attachment in `DownloadForegroundService.onStartCommand(ACTION_SCHEDULE_WORK)`;
- shared app-private production database naming through `offline-yt-player.sqlite3` across MainActivity, worker execution, and foreground-service control dispatch;
- deterministic Android runtime proof in `GeneratedUniffiCoreGatewaySmokeTest`, which seeds durable queue/work, starts the real foreground-service schedule action, serves fixture bytes from a local deterministic HTTP endpoint, waits for the real core worker to complete, and asserts a completed durable Library item and local asset.

## Exact-head qualification

Exact master `3d235ba58d99c6d5c590e5fdc74cac6e4d417980` passed all required exact-head workflows:

- CI: `36562241183`
- Android smoke: `36562241196`
- Android FGS timeout: `36562241279`
- Supply chain: `36562241197`
- CI evidence: `36562241172`
- Deterministic E2E fixture: `36562241201`

The Android smoke lane is the decisive RMD-502a runtime proof because it includes the scheduled-fixture foreground-service path through the real packaged app/runtime/worker boundary into completed durable Library state. The API-35 foreground-service lane separately qualifies the retained Android 15+ timeout handling surface.

## Result

RMD-502a is implemented and qualified on exact master `3d235ba58d99c6d5c590e5fdc74cac6e4d417980`. The canonical remediation TODO should mark RMD-502a complete and cite the workflow run IDs above.
