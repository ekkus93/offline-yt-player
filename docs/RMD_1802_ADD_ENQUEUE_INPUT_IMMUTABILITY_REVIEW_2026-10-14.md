# RMD-1802 — Add-screen input immutability during pending enqueue

Date: 2026-10-14

**Status:** Source and packaged behavioral regression committed to `master`; exact-head qualification pending.

## Finding

`AddScreen` already disabled Download and Options while its asynchronous `AppDownloadControlGateway.enqueue` call was outstanding. It did not disable the source URL `OutlinedTextField` or Paste. A user could therefore mutate the displayed URL and clear the resolved setup while an earlier source's download was still being scheduled. A late scheduler result could then be shown against a different visible URL.

## Correction

Disable the Add URL editor and Paste while `scheduling` is true. Existing `enabled` guards on Download/Options/Analyze remain, and the durable background request retains the original immutable `jobId` and selection IDs. Once scheduling settles, both controls become available again.

## Behavioral proof

The existing packaged API-29 `DownloadSetupSingleFlightInstrumentedTest.enqueueCannotBeSubmittedTwiceWhileFirstRequestIsPending` deliberately blocks the real app-to-gateway enqueue call off-main and asserts that Download, Options, Paste, and URL editing are disabled while it is pending, then verifies one enqueue and a reported completion after release.

- [ ] Pass exact-head Android instrumentation smoke including this method.
- [ ] Pass Rust/core, JVM, UniFFI/ABI, deterministic E2E, cold-start, FGS timeout, real-network, supply-chain and CI evidence on the same final `master` SHA.
- [ ] Complete independent RMD-1802 review and canonical TODO reconciliation with exact evidence.
- [ ] Preserve external YouTube/service-policy/legal release approval as an unresolved independent gate.
