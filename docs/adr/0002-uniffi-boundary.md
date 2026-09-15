# ADR 0002: UniFFI for the portable core boundary

Status: accepted for v1 prototype

## Decision

Use UniFFI's procedural-macro interface for the Rust/Kotlin boundary. The exported surface is deliberately coarse: immutable summaries, curated quality choices, typed error categories, and durable job identifiers. Provider-specific stream types remain inside Rust.

Android must invoke blocking core operations from a background dispatcher. Long-running operations use explicit durable job IDs and explicit pause/resume/cancel methods; they do not depend on cancellation of a Kotlin coroutine propagating magically into Rust. This follows UniFFI's cancellation model, where application-specific cancellation channels are required.

## Android ABI/build integration

The Rust crate remains a `cdylib`. Android packaging will build the supported Android ABI set and place the resulting shared libraries under the app's JNI library inputs; generated Kotlin bindings are compiled into the app module. The CI milestone for OYP-303 must generate Kotlin bindings and exercise a representative Android ABI before this boundary is considered production-qualified.

## Fallback criteria

Switch to a narrowly scoped manual JNI layer only if a measured UniFFI limitation blocks one of these requirements: supported Android ABI packaging, stable Kotlin generation under the pinned toolchain, typed coarse records/errors, or acceptable call overhead after progress coalescing. A preference for handwritten JNI or hypothetical performance concerns are not sufficient reasons to abandon UniFFI.

## Threading and cancellation

Blocking filesystem, database, resolver, and transfer calls are forbidden on Android's main thread. Kotlin owns lifecycle dispatch; Rust owns durable state. Cancellation is explicit and idempotent at the job API rather than implicit foreign-future cancellation.
