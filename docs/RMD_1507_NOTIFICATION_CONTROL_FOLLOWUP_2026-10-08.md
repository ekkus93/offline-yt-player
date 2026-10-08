# RMD-1507 notification-control production-path audit — 2026-10-08

The canonical checklist is `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; this note is supporting evidence, not a competing checklist.

## Defect found

`DownloadForegroundService.onStartCommand` dispatches notification Pause/Resume/Cancel synchronously through `GeneratedUniffiDownloadControlGateway`. The generated gateway checks that blocking FFI calls do not run on the Android main thread. This path therefore needs an off-main execution boundary and a device-side test proving each notification action changes durable state.

A successful Resume also needs to wake Android's runtime worker. The direct notification path currently passes the raw gateway to `DownloadForegroundControlDispatcher`, not `SchedulingDownloadControlGateway`. Without a fresh schedule, an idle worker need not restart. Do not treat the existing dispatcher JVM test, which uses a fake gateway, as proof of notification-to-worker E2E.

## Incremental correction on master

`SchedulingDownloadControlGateway` now schedules work after successful durable Resume and Retry transitions, as well as Enqueue. `SchedulingDownloadControlGatewayResumeRetryTest` exercises both wakeups, Wi-Fi-only propagation, scheduler rejection, and suppression after a rejected durable transition. Exact-head CI qualification remains required.

## Remaining qualification

The foreground notification service must perform blocking FFI off the main thread and route successful Resume through the production scheduling adapter. RMD-1507 remains unchecked until actual notification PendingIntent actions, durable queue state, UI mirroring, and resumed worker behavior are exercised on a device, with exact-head CI evidence.
