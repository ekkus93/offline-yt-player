# RMD-500 durable download orchestration reconciliation — 2026-09-20

This evidence note reconciles the detailed RMD-500 criteria against production Rust/Android paths. It does not replace the detailed remediation TODO.

## RMD-501 — durable queue is authoritative

The core durable download snapshot/state machine persists queue state in SQLite and exposes it through the UniFFI/core gateway. Android reads the durable queue rather than treating service-local flags as authoritative. Queue records include queued/running/paused/retry/failed/cancelled/completed semantics and survive process recreation.

## RMD-502 — worker execution loop

`core/src/worker.rs` claims only eligible durable work, applies the core concurrency bound, executes the real transfer engine, persists bounded progress, promotes completed assets/library metadata only after successful transfer/integrity handling, and repairs interrupted running claims after process death. Rust behavioral tests cover successful execution, concurrency, retry, cancellation, and interrupted-claim repair.

## RMD-503 — Pause

UI and notification controls converge on the app download-control gateway. The worker checks durable stop state at bounded cancellation points; pause remains distinct from terminal cancellation and preserves resumable state/partial data according to transfer policy. Rust worker tests include durable pause behavior and Android control-path tests cover gateway routing.

## RMD-504 — Resume

Resume moves paused durable work back to scheduler eligibility through the same control gateway. Range continuation validation lives in the transfer/download path, network preference is reapplied before execution, and durable queue state permits process-death recovery rather than relying on an in-memory service flag.

## RMD-505 — Cancel

Cancel uses the shared durable control path, stops active transfer at a bounded cancellation point, persists terminal cancelled state, and applies partial-asset cleanup policy. UI and notification dispatchers use the same gateway rather than independent state.

## RMD-506 — Retry

Retry is constrained to eligible failed durable records, preserves source/library identity, resets the retry-specific state required for a new attempt, and remains bounded by the authoritative retry policy. Core worker/persistence tests cover attempt and retry-state semantics.

## RMD-507 — progress/speed/ETA

The transfer path propagates transferred/total bytes and coalesces persisted progress. Android row policy derives user-visible progress from actual durable values; unknown total length does not invent a numeric completion percentage. Speed/ETA policy uses bounded observations and only exposes ETA when enough information exists.

## RMD-508 — connectivity integration

`AndroidDownloadConnectivityObserver` maps Android `NetworkCapabilities` into bounded app connectivity state. `DownloadConnectivityGate` pauses active work through the same durable control gateway when policy disallows the network, enforces the unmetered/Wi-Fi preference, retains waiting work, and reschedules it through `DownloadExecutionScheduler` when constraints return. `DownloadConnectivityInstrumentationTest` provides Android instrumentation coverage for the connectivity transition integration, supplemented by deterministic JVM policy/gate tests.

## Qualification

RMD-500 relies on the regular exact-head Rust fmt/clippy/test, Android JVM/lint/build, UniFFI/APK, and remediation-governance lanes. This reconciliation must pass exact-head CI before merge. Broader device E2E qualification remains governed separately by RMD-1400/RMD-1500/RMD-1800; this note does not substitute policy-only tests for those end-to-end gates.
