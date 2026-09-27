# RMD-508 Canonical TODO Reconciliation Plan — 2026-09-27

## Scope

This document records exact evidence for reconciling RMD-508 in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

It is not a replacement TODO. The canonical remediation TODO remains the sole completion checklist and must preserve its full detailed structure.

## Evidence base

- Current master: `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`.
- RMD-508 implementation PR: #387.
- PR #387 head: `22fcaa4287f7739d17e075404b0eec1e1e697def`.
- PR #387 merge commit: `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`.
- Exact-head PR #387 qualification on `22fcaa4287f7739d17e075404b0eec1e1e697def`:
  - Push CI `36304610292`, Android smoke `36304610289`, Android FGS timeout `36304610297`, and Supply chain `36304610324` passed.
  - Pull-request CI `36305482450`, Android smoke `36305482422`, Android FGS timeout `36305482427`, and Supply chain `36305482402` passed.
- Post-merge master qualification on `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`:
  - CI `36306527110` passed.
  - Supply chain `36306527188` passed.
  - Android FGS timeout `36306527158` passed.
  - Android smoke `36306527131` passed.

## Supported canonical TODO checkbox changes

The next canonical TODO edit should mark all RMD-508 subtasks complete and add an evidence paragraph immediately after the RMD-508 checklist:

### RMD-508 — Connectivity integration

- `Observe Android network capability changes.`
  - Evidence: `AndroidDownloadConnectivityObserver` registers a default network callback and emits normalized connectivity changes.
- `Map to core/app connectivity state.`
  - Evidence: `DownloadConnectivityMapper` maps Android `NetworkCapabilities` into the app's `DownloadConnectivity.None`, `Metered`, and `Unmetered` states; JVM and instrumentation tests cover the mapping.
- `Pause/wait when no usable network exists.`
  - Evidence: `DownloadConnectivityCoordinator` applies `DownloadNetworkPolicy` to the durable queue and calls the shared `AppDownloadControlGateway.pause` path for active `RESOLVING`, `DOWNLOADING`, and `VERIFYING` work when policy returns `PauseForConnectivity`.
- `Enforce Wi-Fi/unmetered preference.`
  - Evidence: `DownloadNetworkPolicy` rejects metered connectivity for `WifiOnly`; `SchedulingDownloadControlGateway` schedules work from current settings; `DownloadConnectivityCoordinator` consumes the current preference function before applying queue controls.
- `Automatically make waiting work eligible when constraints return.`
  - Evidence: `DownloadConnectivityCoordinator` calls the shared `AppDownloadControlGateway.resume` path for durable `PAUSED` jobs when connectivity policy returns `Allow`.
  - Note: the current production model uses durable `PAUSED` as the constrained waiting state rather than introducing a separate network-wait enum.
- `Add instrumentation tests for transitions.`
  - Evidence: `DownloadConnectivityCoordinatorTest` covers lost-connectivity pause, restored-connectivity resume, and Wi-Fi-only metered behavior. `DownloadConnectivityObserverInstrumentedTest` executes Android framework capability mapping in the packaged instrumentation lane, and `.github/workflows/android-smoke.yml` includes it in Android smoke.

## Evidence paragraph draft for canonical TODO

`**Evidence (RMD-508):** Android connectivity observation and queue policy are implemented by `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityObserver.kt`. `AndroidDownloadConnectivityObserver` registers a default-network callback and emits normalized `DownloadConnectivity` states; `DownloadConnectivityMapper` maps Android `NetworkCapabilities` into `None`, `Metered`, and `Unmetered`; and `DownloadConnectivityCoordinator` applies `DownloadNetworkPolicy` plus the current download preference to the durable queue through the same `AppDownloadControlGateway` pause/resume methods used by UI and notification controls. Active `RESOLVING`, `DOWNLOADING`, and `VERIFYING` jobs are paused when connectivity is unusable or violates Wi-Fi-only policy, while durable `PAUSED` jobs become eligible again through resume when constraints return. JVM coverage is in `DownloadConnectivityCoordinatorTest` and existing network-policy tests; packaged Android framework mapping coverage is in `DownloadConnectivityObserverInstrumentedTest`, which is included in `.github/workflows/android-smoke.yml`. Qualified/merged evidence: PR #387 exact head `22fcaa4287f7739d17e075404b0eec1e1e697def` passed push CI `36304610292`, push Android smoke `36304610289`, push Android FGS timeout `36304610297`, push Supply chain `36304610324`, PR CI `36305482450`, PR Android smoke `36305482422`, PR Android FGS timeout `36305482427`, and PR Supply chain `36305482402`; PR #387 merged as `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`, whose post-merge master CI `36306527110`, Supply chain `36306527188`, Android FGS timeout `36306527158`, and Android smoke `36306527131` passed.`

## Explicitly not closed here

- RMD-1506 connectivity E2E remains unchecked. RMD-508 now has production connectivity observation, policy mapping, durable pause/resume controls, JVM tests, and instrumentation mapping coverage, but RMD-1506 still requires a deterministic end-to-end transfer with network removal/restoration.
- RMD-1601 deterministic E2E fixture lane remains unchecked.
- RMD-1803 final full qualification remains unchecked.
