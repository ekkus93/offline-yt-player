# Rust/Kotlin FFI qualification audit

This audit records the current status of OYP-301 and the already-qualified portions of OYP-303. It deliberately does **not** claim OYP-302 complete: the current exported surface is still smaller than the required application API.

## OYP-301 — Select and configure FFI

UniFFI is the accepted v1 mechanism. ADR-002 records the decision and the bounded manual-JNI fallback criteria: JNI is permitted only if a required API cannot be represented safely by the pinned UniFFI version, generated bindings cannot satisfy supported Android ABI/build requirements, or measured FFI behavior creates an otherwise-unfixable correctness/performance problem. Convenience alone is not a fallback criterion.

The repository pins the binding generator under `tools/uniffi-bindgen/`. CI's `UniFFI Kotlin and Android ABI` job generates Kotlin bindings and cross-builds the Rust core for representative `aarch64-linux-android`, proving the selected mechanism is integrated with the Android/native toolchain rather than existing only as a host-side prototype.

## OYP-303 — qualification already present

`core/src/ffi.rs` defines UniFFI records/enums for representative source, media-summary, curated-quality and typed-error data. Rust tests round-trip representative domain values into those records and verify error-category/retryability mapping. ADR-002 requires blocking Rust work to execute off the Android main thread and makes explicit cancellation/durable state authoritative.

These facts qualify the representative-type and typed-error portions of OYP-303, but OYP-303 must remain open until the expanded OYP-302 operation surface has cancellation tests and Android-side proof that blocking calls are dispatched off the main thread.

## OYP-302 gap

The current `#[uniffi::export]` surface exposes `ffi_core_identity()` but does not yet expose the TODO-required coarse operations for resolve, list choices, enqueue, pause, resume, cancel, library list/get, and delete. Therefore OYP-302 is explicitly **not** reconciled by this audit. The next implementation slice should add those operations behind a coarse service/object boundary, with durable job identifiers and Kotlin-safe errors, before OYP-302/OYP-303 closeout.

## Evidence

The baseline/bootstrap audit documents the pinned Android/Rust toolchains and CI structure. The exact-head CI run for this audit commit is the qualification gate; TODO reconciliation should cite the passing SHA/run rather than predicting it here.
