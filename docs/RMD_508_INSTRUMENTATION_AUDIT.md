# RMD-508 Instrumentation Audit

This slice adds explicit instrumentation coverage for the Android connectivity transition contract.

## Implemented

- Added `DownloadConnectivityInstrumentationTest` under `app/src/androidTest`.
- The instrumented test binds to Android's real `ConnectivityManager`, without trying to mutate device network state.
- The test then drives the same policy states produced by the production `AndroidDownloadConnectivityObserver` through `DownloadConnectivityGate`:
  - no usable network pauses active work,
  - metered connectivity keeps Wi-Fi-only work waiting,
  - unmetered connectivity re-schedules waiting work.
- Added a JVM contract test that ensures the instrumentation source remains present and covers all required transition symbols in ordinary CI.

## Notes

The CI environment currently runs JVM unit tests, lint, APK assembly, Rust qualification, and UniFFI/ABI checks. It does not provision an emulator for connected Android tests, so the JVM contract test keeps the instrumentation source from silently disappearing while the device-side test remains available for emulator/device validation.
