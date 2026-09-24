# RMD-508 connectivity integration implementation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-508.

## Implementation

RMD-508 is implemented as an Android foreground-service integration slice over the existing durable queue control gateway.

- `DownloadConnectivityObserver` registers an Android `ConnectivityManager.NetworkCallback` and emits the current mapped connectivity state on availability, loss, and capability changes.
- `NetworkCapabilities?.toDownloadConnectivity(...)` maps Android platform capabilities into core/app connectivity states: `None`, `Metered`, and `Unmetered`.
- `DownloadConnectivityCoordinator` applies the existing `DownloadNetworkPolicy` to the active queue item and dispatches durable `pause`/`resume` through `AppDownloadControlGateway`.
- The coordinator tracks only jobs paused by connectivity in the current service process, so a restored network does not resume a job that the user paused manually.
- `DownloadForegroundService` now starts/stops the observer with the service lifecycle, stores the active queue item id, and re-emits connectivity on retry/reconcile/schedule intents.

## Behavioral proof

JVM tests cover the policy-to-gateway boundary without requiring emulator runtime:

- `noNetworkPausesActiveDurableJob`
- `wifiOnlyPausesOnMeteredAndResumesOnlyItsOwnConnectivityPause`
- `restoredNetworkDoesNotResumeUserPausedJobThatConnectivityDidNotPause`
- `missingQueueItemDoesNotGuessWhichJobToMutate`

Existing policy tests continue to cover Wi-Fi-only and no-network decision semantics.

## Qualification policy

The primary proof is JVM boundary behavior because the observer maps Android connectivity into durable core gateway calls. Exact-head CI, Android smoke, and API-35 timeout qualification remain required before merge.
