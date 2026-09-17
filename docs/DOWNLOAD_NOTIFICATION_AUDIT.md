# Download Notification Audit

## Scope

OYP-1002 qualifies the Android notification contract for active and terminal download state.

## Implementation

- Active transfer work is represented by an ongoing low-importance foreground notification with progress presentation.
- Pause, resume, and cancel are visible notification actions backed by explicit immutable service `PendingIntent`s.
- `DownloadServicePolicy.ReportsCompletionAndFailure` makes terminal completion/failure reporting part of the service contract; the durable Rust download state supplies the terminal outcome consumed by the Android layer.
- `setOnlyAlertOnce(true)` avoids repeated audible/vibration churn while progress updates.

## Qualification

`DownloadNotificationPolicyTest` locks the progress presentation, pause/resume/cancel actions, explicit immutable intent routing, low-importance channel, and completion/failure reporting policy. End-to-end notification delivery under process death remains part of OYP-1004/OYP-1900.
