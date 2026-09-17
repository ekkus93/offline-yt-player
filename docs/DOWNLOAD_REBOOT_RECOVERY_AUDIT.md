# Download Reboot Recovery Audit

## Scope

OYP-1004 qualifies the Android process-death/reboot recovery entry point for durable downloads.

## Implementation

- `DownloadRebootReceiver` is a private broadcast receiver for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`.
- The receiver starts `DownloadForegroundService` through an explicit `ACTION_RECONCILE_AFTER_REBOOT` service action.
- The service remains `START_STICKY`, preserving Android's process-pressure restart behavior for active foreground work.
- `DownloadServicePolicy` records that durable queue reconstruction is required and interrupted transfers must resume or fail explicitly.
- The portable Rust core already owns durable download snapshot loading and startup reconciliation for live-worker states.

## Qualification

`DownloadRebootRecoveryPolicyTest` locks the manifest permission, private receiver declaration, boot actions, explicit foreground-service recovery action, durable-queue reconciliation policy, and explicit interrupted-transfer outcome policy.
