# RMD-1802 — Download Setup single-flight enqueue (2026-10-14)

Independent production review found that `AddScreen` set `scheduling = true` before asynchronously calling the download gateway, but `DownloadSetupPreview` left both Download and Options enabled. Rapid taps could issue duplicate enqueue requests or allow options edits while the previous selection was still being scheduled.

The production preview now disables both buttons while `isScheduling` is true, and the Download callback independently guards against reentry. This does not alter durable queue identity or legitimate post-settlement retry behavior.

The packaged Compose regression `DownloadSetupSingleFlightInstrumentedTest.enqueueCannotBeSubmittedTwiceWhileFirstRequestIsPending` blocks the actual gateway boundary on a latch, verifies it executes off the UI thread, and asserts Download/Options are disabled and only one enqueue was submitted while pending. The mandatory API-29 smoke runner invokes this exact test and checks its XML evidence.

Implementation and test wiring are not qualification: require passing exact-head main CI and Android smoke plus the full eight-workflow matrix. Keep RMD-1802 independent review, RMD-1803/1805 closeout, and external YouTube/service/legal approval open.
