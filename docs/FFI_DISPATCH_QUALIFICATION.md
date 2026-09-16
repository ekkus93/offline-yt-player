# FFI dispatch qualification

OYP-303 requires blocking Rust/UniFFI calls not to execute on the Android main thread. `CoreCallDispatcher` is the Android-side boundary for any core call that may block on persistence, source resolution, or download work. It owns a dedicated `offline-yt-core-worker` executor rather than executing submitted work inline on the calling/UI thread.

The v1 dispatcher deliberately uses one worker. This both prevents blocking core work from occupying the Android main thread and establishes a bounded application-level concurrency policy rather than allowing arbitrary callers to create unbounded worker threads.

`CoreCallDispatcherTest` submits representative blocking work from the JVM test thread and proves that execution occurs on the dedicated worker. It also proves sequential submissions use the bounded single-worker executor. The dispatcher is `Closeable`, and closing it interrupts queued/running work through `shutdownNow`; long-running Rust operations additionally use the explicit cooperative `FfiCancellationToken` for application-level cancellation semantics.

Together with the representative UniFFI record round-trip and typed-error tests in `core/src/ffi.rs`, the cancellation test proves that cancellation is sticky, idempotent, and maps to Kotlin-safe `Canceled`, while the dispatcher tests prove the required off-main-thread blocking-call policy. These are the three qualification obligations in OYP-303, so OYP-303 is complete independently of the still-open OYP-302 operation-surface expansion. OYP-302 remains responsible for adding the remaining resolve/list-choices/enqueue/pause/resume/cancel service operations and does not invalidate the already-qualified FFI mechanics.
