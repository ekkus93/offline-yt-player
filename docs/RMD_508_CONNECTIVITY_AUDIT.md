# RMD-508 Connectivity Audit

This slice replaces connectivity policy-only assertions with a production Android observation path.

- `AndroidDownloadConnectivityObserver` registers a `ConnectivityManager.NetworkCallback` for Internet-capable networks and publishes an initial state immediately.
- `DownloadConnectivityMapper` converts Android capabilities into the existing bounded `None` / `Metered` / `Unmetered` policy states without leaking Android network objects into the portable control model.
- `None` is emitted when there is no active usable Internet capability; metered and unmetered Internet are distinguished through `NET_CAPABILITY_NOT_METERED`.
- Duplicate states are coalesced before reaching downstream orchestration.
- The existing `DownloadNetworkPolicy` makes `None` wait for all downloads and makes `Metered` wait for `WifiOnly`; the API 34+ scheduler independently applies `NETWORK_TYPE_UNMETERED` for the same preference.
- Unit coverage verifies capability-to-policy-state mapping. The remaining RMD-508 slice will connect observed transitions to durable waiting-work re-eligibility and add transition-level instrumentation evidence.
