# RMD-1802 — UIDT uncaught worker exception review (2026-10-13)

## Finding

`DownloadUserInitiatedJobService.onStartJob` launched a plain thread calling `runAndSettleDownloadJob`. Its `finally` correctly invoked the JobScheduler settlement callback, but an unexpected `Exception` thrown by the native execution or settlement callback propagated beyond the thread body. Android's default uncaught-exception handler can terminate the application process, disrupting unrelated downloads and UI.

## Correction

`app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt` now uses `runAndSettleDownloadJobSafely` at the UIDT thread boundary. It preserves the existing `finally` settlement and catches unexpected exceptions, emitting a fixed, non-sensitive warning. It does not log exception messages, stack traces, provider URLs, or secrets. Interrupted durable work still requires startup reconciliation; this does not claim automatic completion or bypass platform-stop semantics.

`app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionSchedulerPolicyTest.kt` adds `unexpectedUidtWorkerExceptionDoesNotEscapeAfterDurableJobSettlement`, checking a thrown exception cannot escape the wrapper, settlement occurs exactly once, and a successful run does not report a failure.

## Qualification

Pending exact-head CI including Android JVM and API-35 UIDT instrumentation. This is an independent RMD-1802 finding, not final RMD-1802/1803/1805 closeout or external policy/legal approval.
