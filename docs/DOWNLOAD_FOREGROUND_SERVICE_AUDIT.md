# Download Foreground Service Audit

## Scope

OYP-1001 qualifies the Android foreground-service boundary for durable downloads.

## Implementation

- `DownloadForegroundService` is declared as a non-exported `dataSync` foreground service and the manifest requests the corresponding foreground-service permission.
- `onStartCommand` immediately promotes active work to a user-visible foreground notification and returns `START_STICKY`, allowing Android to recreate the service after process pressure.
- `ACTION_STOP` removes the foreground notification and stops the service explicitly.
- `DownloadServicePolicy.MaxConcurrentDownloads` bounds simultaneous transfer work to two; durable queue reconstruction is an explicit service policy and the portable durable queue remains owned by the Rust core.
- Notification actions are explicit service intents rather than hidden gestures.

## Qualification

`DownloadForegroundServicePolicyTest` locks the manifest contract, user-visible foreground promotion, explicit stop lifecycle, sticky restart policy, bounded concurrency, and durable-queue-reconciliation policy. Process-death/reboot reconstruction remains separately tracked by OYP-1004 and end-to-end qualification.
