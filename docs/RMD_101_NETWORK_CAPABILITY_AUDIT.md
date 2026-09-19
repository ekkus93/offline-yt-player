# RMD-101 Network Capability Audit

RMD-101 requires both static manifest evidence and device-side proof that the packaged Android application can perform network I/O.

## Evidence

- `app/src/main/AndroidManifest.xml` declares `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`; no broad storage or location permissions are requested.
- `ManifestPermissionTest` keeps those manifest requirements under regular JVM CI.
- `NetworkCapabilitySmokeTest` is an Android instrumentation test that opens a deterministic loopback HTTP fixture from the packaged app process and verifies the response body.
- `NetworkCapabilityInstrumentationContractTest` keeps the device-side smoke test present under regular JVM CI even when CI has no emulator attached.

The fixture binds only to `127.0.0.1`, uses bounded connect/read timeouts, and does not depend on external network availability or provider behavior.
