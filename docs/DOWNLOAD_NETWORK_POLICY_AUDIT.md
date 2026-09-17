# Download Network Policy Audit

## Scope

OYP-1003 defines the deterministic Android download-service policy for user network preferences and connectivity loss.

## Implementation

`DownloadNetworkPolicy` classifies the user's download preference and current connectivity into an explicit `DownloadNetworkDecision`:

- `AnyNetwork` allows metered and unmetered connectivity but pauses when connectivity is absent.
- `WifiOnly` allows only unmetered connectivity and pauses on metered or absent connectivity.
- Silent preference violation is explicitly disallowed.
- The download foreground service exposes a `CONNECTIVITY_RETRY` action so connectivity-driven retry/resume can be routed through the same explicit service-intent boundary as user-visible download actions.

## Qualification

`DownloadNetworkPolicyTest` locks Wi-Fi-only behavior, connectivity-loss pause/retry behavior, and the no-silent-preference-violation invariant. Android broadcast/network-callback wiring remains a later integration concern, but the service policy now has a deterministic testable contract for that wiring to consume.
