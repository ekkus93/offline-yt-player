# Android Qualification Acceleration Plan — 2026-09-22

This plan records the revised remediation strategy for the Offline YT Player remediation track. The goal is to avoid treating Android qualification as one giant late-stage gate while preserving exact-head evidence and final release discipline.

## Strategy

Use tiered Android qualification:

1. **Fast PR gate**
   - Rust fmt, clippy, and tests.
   - Android lint, JVM tests, and assemble/package checks.
   - UniFFI generation consistency and native-library packaging verification.
   - A small Android instrumentation smoke lane once RMD-1401 infrastructure exists.

2. **Explicit full qualification gate**
   - Full emulator instrumentation.
   - Behavioral Compose tests.
   - Screenshot/golden tests.
   - Deterministic fixture E2E flows.
   - Accessibility/layout checks.
   - Dependency/license/advisory checks.

3. **Move non-runtime proof off emulator**
   - URL validation, metadata bounding, diagnostic redaction, path safety, resource bounds, settings mapping, recovery state mapping, and gateway conversion should be tested with Rust/JVM tests wherever Android runtime behavior is not required.

4. **Keep live-provider proof isolated**
   - Live YouTube qualification remains opt-in/manual or otherwise isolated from normal PR gates. Deterministic fixture E2E must still exercise the same app pipeline: Add/Share → Analyze → Download Setup → Scheduler → Core worker → Library → Playback.

## Immediate execution order

1. Finish RMD-1302 evidence reconciliation after PR #310.
2. Complete RMD-1303 and RMD-1304 as bounded security/resource-hardening tasks.
3. Add the smallest useful Android smoke lane for RMD-1401 rather than attempting the full Android matrix in one step.
4. Reconcile already-implemented RMD-100 through RMD-700 evidence before writing redundant tests.
5. Build full RMD-1400/RMD-1500 qualification incrementally after the smoke lane is reliable.

## Non-goals

- This plan does not weaken final closeout requirements.
- This plan does not mark external YouTube/service-policy/legal approval complete.
- This plan does not allow policy-only tests to stand in for production-path behavior where the TODO requires runtime evidence.
