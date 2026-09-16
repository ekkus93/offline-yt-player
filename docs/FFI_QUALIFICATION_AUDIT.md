# Rust/Kotlin FFI qualification audit

This audit records the current status of OYP-301 and the already-qualified portions of OYP-303. It deliberately does **not** claim OYP-302 complete: the current exported surface is still smaller than the required application API.

## OYP-301 — Select and configure FFI

UniFFI is the accepted v1 mechanism. ADR-002 records the decision and the bounded manual-JNI fallback criteria: JNI is permitted only if a required API cannot be represented safely by the pinned UniFFI version, generated bindings cannot satisfy supported Android ABI/build requirements, or measured FFI behavior creates an otherwise-unfixable correctness/performance problem. Convenience alone is not a fallback criterion.

The repository pins the binding generator under `tools/uniffi-bindgen/`. CI's `UniFFI Kotlin and Android ABI` job generates Kotlin bindings and cross-builds the Rust core for representative `aarch64-linux-android`, proving the selected mechanism is integrated with the Android/native toolchain rather than existing only as a host-side prototype.

## OYP-303 — qualification already present

`core/src/ffi.rs` defines UniFFI records/enums for representative source, media-summary, curated-quality and typed-error data. Rust tests round-trip representative domain values into those records and verify error-category/retryability mapping.

`FfiCancellationToken` is an exported, thread-safe cooperative cancellation object. Cancellation is sticky and idempotent; bounded Rust interruption checks convert cancellation into the same typed `Canceled` error exposed to Kotlin. Its unit test proves uncanceled operation, repeated cancellation, sticky state, and typed error conversion.

On Android, `CoreCallDispatcher` is the required boundary for blocking Rust/UniFFI work. It owns a bounded single-worker `offline-yt-core-worker` executor rather than executing submitted work inline on the caller/UI thread. `CoreCallDispatcherTest` proves representative blocking work executes off the caller thread and that sequential submissions remain bounded to that worker. `docs/FFI_DISPATCH_QUALIFICATION.md` records the dispatcher contract and shutdown behavior.

These facts qualify all three OYP-303 checklist requirements at the infrastructure/policy level: representative domain round-trip, errors and cooperative cancellation, and off-main-thread dispatch for blocking calls. End-to-end generated-binding qualification of the eventual application operations remains coupled to OYP-302 and must be extended as that surface lands; this audit does not use that dependency to erase the already-qualified OYP-303 infrastructure.

## OYP-302 gap

The current `#[uniffi::export]` surface exposes `ffi_core_identity()` and `FfiCancellationToken`, but it does not yet expose the TODO-required coarse operations for resolve, list choices, enqueue, pause, resume, cancel, library list/get, and delete. Therefore OYP-302 is explicitly **not** reconciled by this audit. The next implementation slice should add those operations behind a coarse service/object boundary, with durable job identifiers and Kotlin-safe errors.

The cancellation semantics for that surface are now defined: long-running operations receive an `FfiCancellationToken`, check it at bounded interruption points, and return typed cancellation without depending on foreign-future cancellation behavior. Durable download state remains authoritative across process/lifecycle boundaries.

## Evidence

Representative-type/error qualification was established by the earlier FFI audit. Cooperative cancellation landed in PR #86 and blocking Android dispatch landed in PR #87. Exact-head master CI passed at `03d09c5301ff11ca5e9f591409cafd6e9637b3f8` after PR #87. The CI run for this documentation refresh is an additional exact-head consistency gate; canonical TODO reconciliation should cite the passing SHA/run once merged.
