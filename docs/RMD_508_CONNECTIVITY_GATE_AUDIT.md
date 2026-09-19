# RMD-508 Connectivity Gate Audit

This slice extends the Android connectivity observer work with a deterministic gate between observed network state, durable download control, and scheduler eligibility.

## Implemented

- `DownloadConnectivityGate` pauses active work through `AppDownloadControlGateway.pause()` when current connectivity violates the work item's `DownloadNetworkPreference`.
- Waiting work is retained by queue item id and original network preference.
- Connectivity restoration re-schedules waiting work through the shared `DownloadExecutionScheduler` only when `DownloadNetworkPolicy` permits it.
- Wi-Fi-only work remains waiting on metered connectivity.
- Scheduler rejection leaves waiting work recorded instead of losing eligibility state.
- Unit coverage exercises no-network pause, Wi-Fi-only pause, restored unmetered reschedule, still-waiting transitions, and scheduler rejection.

## Remaining RMD-508 work

- Wire the connectivity gate into the long-lived download execution owner once the production download repository/ViewModel wiring exists.
- Add instrumentation coverage around real Android `ConnectivityManager` callbacks and service lifecycle transitions.
- Reconcile the master TODO checkboxes only after the observer, gate, production wiring, and instrumentation evidence are all merged.
