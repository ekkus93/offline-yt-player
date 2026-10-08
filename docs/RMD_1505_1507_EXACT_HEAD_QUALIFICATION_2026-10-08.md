# RMD-1505 and RMD-1507 Android qualification — 2026-10-08

Exact implementation commit: `bcd298d7e87db55dee16d3ab485c9214998c8033` (master).

All six required workflows passed on this exact SHA:

- CI: `37767072682` (success)
- Android smoke: `37767072673` (success)
- Android FGS timeout: `37767072664` (success)
- Supply chain: `37767072641` (success)
- CI evidence: `37767072745` (success)
- Deterministic E2E fixture: `37767072764` (success)

`Rmd1505StorageFailureInstrumentedTest` exercises generated worker preflight for an impossible 64 GiB plan, durable nonretryable failure, no partial/final asset, no completed Library item, and actionable production Downloads UI error. It does not exercise a mid-write ENOSPC failure or mid-transfer partial recovery.

`Rmd1507NotificationControlInstrumentedTest` invokes the actual foreground notification Pause, Resume, and Cancel PendingIntents and verifies durable `QUEUED → PAUSED → QUEUED → CANCELED` state and production Downloads UI presentation after each action. The `CANCELED` UI mapping regression is covered by JVM tests.

`Rmd1501AndroidRuntimeFixtureInstrumentedTest.scheduledFixtureReopensOfflineAndPlaysViaCanonicalMediaSession` proves scheduled fixture download, Library persistence, repository reopen, and MediaController playback with the fixture server shut down; it does not cover the complete Add/Share pipeline or OS process-kill recovery.

Canonical completion authority remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. The relevant checkboxes must be reconciled there; this evidence document is not a replacement TODO.
