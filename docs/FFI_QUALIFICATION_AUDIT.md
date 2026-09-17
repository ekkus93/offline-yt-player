# Rust/Kotlin FFI qualification audit

This audit records the current OYP-301 through OYP-303 boundary and qualification state.

## OYP-301 — Select and configure FFI

UniFFI is the accepted v1 mechanism. ADR-002 records the decision and the bounded manual-JNI fallback criteria: JNI is permitted only if a required API cannot be represented safely by the pinned UniFFI version, generated bindings cannot satisfy supported Android ABI/build requirements, or measured FFI behavior creates an otherwise-unfixable correctness/performance problem. Convenience alone is not a fallback criterion.

The repository pins the binding generator under `tools/uniffi-bindgen/`. CI's `UniFFI Kotlin and Android ABI` job generates Kotlin bindings and cross-builds the Rust core for representative `aarch64-linux-android`, proving the selected mechanism is integrated with the Android/native toolchain rather than existing only as a host-side prototype.

## OYP-302 — stable coarse-grained API

The required operation surface is now exported through operation-sized UniFFI objects rather than leaking repository or provider internals:

- `FfiCoreService` exposes library list/get/delete operations over portable `FfiLibraryItem` records.
- `FfiDownloadControlService` exposes durable enqueue/pause/resume/cancel operations. Enqueue persists a stable queued job identifier; repeated enqueue and repeated target-state controls are idempotent; legal transitions are enforced through the portable state machine.
- `FfiSourceService` exposes resolve and list-choices operations. The deterministic constructor registers the direct fixture adapter so the exact foreign-call shape is executable in CI without live-provider dependence. Production source adapters join the same portable source boundary as their provider-specific qualification lands.
- all operation failures are represented through Kotlin-safe `FfiError`/`FfiErrorKind` values, while service-construction persistence failure is a typed UniFFI error rather than a panic.

The API deliberately uses synchronous operation-sized foreign calls plus explicit Android dispatch rather than relying on foreign-future scheduling. Potentially blocking calls must run through `CoreCallDispatcher`. Long-running/cooperatively interruptible source operations receive `FfiCancellationToken`; durable download controls use persisted job state so lifecycle/process boundaries do not depend on an in-memory foreign future. This is the v1 async/cancellation contract.

These facts satisfy the OYP-302 API-shape and cancellation-semantics requirements. Provider completeness, transfer orchestration, and Android foreground-service lifecycle remain tracked by their own later milestones and do not require widening or destabilizing this FFI contract.

## OYP-303 — FFI qualification

`core/src/ffi.rs` defines UniFFI records/enums for representative source, media-summary, curated-quality, library, and typed-error data. Rust tests round-trip representative domain values into those records and verify error-category/retryability mapping.

`FfiCancellationToken` is an exported, thread-safe cooperative cancellation object. Cancellation is sticky and idempotent; bounded Rust interruption checks convert cancellation into the same typed `Canceled` error exposed to Kotlin. Its unit test proves uncanceled operation, repeated cancellation, sticky state, and typed error conversion. `FfiSourceService` additionally proves canceled source operations return that typed error through the coarse API.

On Android, `CoreCallDispatcher` is the required boundary for blocking Rust/UniFFI work. It owns a bounded single-worker `offline-yt-core-worker` executor rather than executing submitted work inline on the caller/UI thread. `CoreCallDispatcherTest` proves representative blocking work executes off the caller thread and that sequential submissions remain bounded to that worker. `docs/FFI_DISPATCH_QUALIFICATION.md` records the dispatcher contract and shutdown behavior.

These facts qualify all three OYP-303 checklist requirements: representative domain round-trip, errors and cooperative cancellation, and off-main-thread dispatch for blocking calls.

## Evidence

Representative-type/error qualification was established by the initial FFI work. Cooperative cancellation landed in PR #86, blocking Android dispatch in PR #87, coarse library operations in PR #89, typed service-open failure in PR #90, durable pause/resume/cancel in PR #92, durable enqueue in PR #93, and deterministic resolve/list-choices in PR #94. PR #94 exact-head push CI `35165307990` and PR CI `35165544505` both passed at `0f4a8408715d1fd233712fcd532a4c77c142f1dc`. Canonical TODO reconciliation should cite the passing closeout SHA/run after this audit update is merged.
