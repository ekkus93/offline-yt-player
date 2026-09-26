# RMD-504 Resume Production Policy Reconciliation — 2026-09-25

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

## Scope

This note records the production-path repair for the RMD-504 requirement that Resume honor current network/settings policy. It does not mark the canonical TODO complete by itself; canonical reconciliation still requires exact-head CI, merge to `master`, and a later TODO evidence update.

## Production change

Before this slice, `SchedulingDownloadControlGateway.resume()` delegated directly to the generated durable control gateway, while `DownloadResumeCoordinator` existed as a tested policy component. That left the production resume action able to bypass the current Wi-Fi-only/current-connectivity decision.

This slice routes production resume through `DownloadResumeCoordinator`:

- `SchedulingDownloadControlGateway.resume()` builds a `DownloadResumeRequest` using the current durable job id and the current `AppSettingsSnapshot.wifiOnlyDownloads` value.
- It supplies the current app connectivity snapshot to `DownloadResumeCoordinator`.
- If current connectivity violates the selected network policy, resume returns `waiting_for_connectivity` without mutating the durable queue or scheduling work.
- If current connectivity allows resume, the coordinator performs the durable `resume` transition and schedules the same durable work item through `DownloadExecutionScheduler`.
- Scheduler rejection is reported explicitly after durable queue acceptance, matching the existing enqueue behavior.

`MainActivity` now owns an `AndroidDownloadConnectivityObserver` lifecycle alongside the existing state refresher. The observer publishes bounded `DownloadConnectivity` state, and the production `SchedulingDownloadControlGateway` receives that snapshot through `connectivitySnapshot = { currentDownloadConnectivity }`.

## Tests

`SchedulingDownloadControlGatewayTest` now covers the production gateway surface:

- Wi-Fi-only + current metered connectivity returns `waiting_for_connectivity`, does not call durable `resume`, and does not schedule work.
- Wi-Fi-only + current unmetered connectivity calls durable `resume` and schedules the job with `DownloadNetworkPreference.WifiOnly`.
- Scheduler rejection after durable resume is reported as `scheduler_rejected` while preserving the shared durable control path.

Existing supporting tests remain in `DownloadResumeCoordinatorTest`, core resume representation tests, durable FFI resume/reopen tests, and process-death recovery tests.

## Qualification note

The local sandbox could not execute Gradle because it cannot resolve `services.gradle.org`; the exact branch head must be qualified through GitHub Actions before any canonical checkbox is reconciled.
