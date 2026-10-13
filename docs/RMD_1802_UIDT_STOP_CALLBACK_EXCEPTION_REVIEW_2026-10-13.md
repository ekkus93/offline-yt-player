# RMD-1802 — UIDT stop-callback exception containment (2026-10-13)

## Independent production-path finding

`DownloadUserInitiatedJobService.onStopJob` invokes `UidtJobAttemptRegistry.stop`, which invokes `UidtJobAttempt.stop` and synchronously calls `DownloadWorkerExecution.requestStop` through the generated native gateway. Before this change, any unexpected `Exception` from that stop signal escaped the framework JobService callback on Android's main thread and could terminate the application. The onStartJob exception cleanup path also invoked this same throwing stop signal.

## Remediation

`UidtJobAttempt.stop` first publishes `Stopped`, then catches unexpected `Exception` from the native stop signal, preserving the no-late-`jobFinished` invariant. The production JobService supplies a fixed non-sensitive warning callback; provider URLs, exception messages, and private storage details are not logged. The attempt remains registered until the native worker exits, preventing a replacement writer from overlapping the original. A failed cooperative stop may leave the worker running until it unwinds; durable startup reconciliation remains necessary.

## Regression and qualification

`DownloadExecutionSchedulerPolicyTest.uidtStopNativeFailureCannotCrashJobServiceOrSettleRevokedAttempt` invokes the actual stop path with a throwing synthetic gateway callback and verifies that no exception escapes, the stop is signaled only once, late settlement is suppressed, and a bounded failure notice is emitted once. It also verifies that the successful stop path does not emit a warning.

This finding is implementation and regression evidence, not final acceptance. Require successful exact-head main CI and relevant Android smoke/E2E evidence before claiming qualification. RMD-1802 independent review, RMD-1803 candidate freeze, RMD-1804/1805, global closeout, and the external policy/legal release gate remain open.

## First exact-head CI failure and correction

The first combined candidate `62b53e6e6342a1382e41645031aff64e3a5e792c` failed main CI run `38394815724`: six existing `DownloadExecutionSchedulerPolicyTest` stop/registry assertions failed. Root cause: adding `onStopFailure` as the *last* function-typed constructor parameter silently rebound Kotlin's existing `UidtJobAttempt { stopNative }` trailing-lambda call sites to the warning callback, leaving the native stop callback empty. The corrected constructor retains `stopNative` as its final parameter while production and new tests use explicit named arguments. Require a new successful exact-head CI matrix; the failing SHA is not qualifying evidence.
