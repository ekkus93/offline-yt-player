# RMD-1802 — Storage settings transient scan retry (2026-10-14)

## Independent finding

The asynchronous Storage summary can fail transiently. With a null summary and fail-closed cleanup controls, the original page exposed no recovery path short of navigating away and reopening Storage settings.

## Production correction

`StorageSettingsPage` now offers an explicit `Retry storage scan` action when accounting is unavailable. It restarts the lifecycle-scoped scan through `Dispatchers.IO` and retains disabled destructive actions while unavailable/loading. A failed scan clears stale summary state.

## Behavioral evidence

The packaged `StorageSettingsRecoveryInstrumentedTest.initialScanFailureCanRetryWithoutLeavingSettings` injects a storage fake that fails once with `IOException`, then succeeds. It verifies bounded error text, disabled destructive controls, successful retry without navigation, refreshed totals, and off-main execution. The API-29 required-smoke harness runs this test in a separate instrumentation invocation and asserts exact-method XML evidence.

This is a new implementation and test requiring its own full exact-head eight-workflow matrix. Independent RMD-1802 review, RMD-1803/1804/1805 closeout, and external service-policy/legal approval remain open.
