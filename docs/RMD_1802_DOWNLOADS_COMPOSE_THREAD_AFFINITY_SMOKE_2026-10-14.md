# RMD-1802 — Downloads Compose thread-affinity smoke failure (2026-10-14)

## Evidence and scope

Exact `master` `5c08a9b25c4b8255c92f1ce7f1c35a6a2c200abe` completed seven of eight workflows successfully. Android smoke run `38549846993`, API-29 job `115804858141`, failed in the individually invoked `Rmd1802DownloadsControlThreadingInstrumentedTest.visibleDownloadsPauseInvokesControlOffMainAndReportsDurableUpdate`. The failure summary reported `android.view.ViewRootImpl$CalledFromWrongThreadException` from `AndroidComposeView.scheduleMeasureAndLayout`. The preceding six required isolated instrumentation classes passed; the twelve pinned golden methods also passed. This is a real failed exact-head qualification, not an acceptable closeout.

The Downloads production handler uses `scope.launch { withContext(Dispatchers.IO) { ... }; message = ... }` to return the result to Compose after off-main gateway invocation. This source structure does not prove that the observed exception is solely a test artifact, and no such root-cause claim is made.

## Bounded harness isolation

The required-smoke script previously ran both tests in `Rmd1802DownloadsControlThreadingInstrumentedTest` within one instrumentation invocation. It now runs each exact test method in its own invocation with fresh result cleanup, distinct log/report names, and an explicit non-skipped method assertion. The first test deliberately exercises a throwing asynchronous Library gateway; the second exercises a successful off-main Downloads control. Separate processes prevent lifecycle or Compose state from one test method contaminating the other.

A same-SHA rerun of failed job `115804858141` was accepted for investigation; acceptance is not proof of success. New harness behavior requires fresh exact-head CI, including API-29 Android smoke. If the isolated Downloads test still fails, investigate the production callback's actual dispatcher and test rule/Compose scheduling using full instrumentation logs before changing production code. Do not delete or weaken either assertion. The independent RMD-1802 review and RMD-1803/1805 closeout remain open. External service-policy/legal approval remains separate.
