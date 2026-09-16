# FFI dispatch qualification

OYP-303 requires blocking Rust/UniFFI calls not to execute on the Android main thread. `CoreCallDispatcher` is the Android-side boundary for any core call that may block on persistence, source resolution, or download work. It owns a dedicated `offline-yt-core-worker` executor rather than executing submitted work inline on the calling/UI thread.

The v1 dispatcher deliberately uses one worker. This both prevents blocking core work from occupying the Android main thread and establishes a bounded application-level concurrency policy rather than allowing arbitrary callers to create unbounded worker threads.

`CoreCallDispatcherTest` submits representative blocking work from the JVM test thread and proves that execution occurs on the dedicated worker. It also proves sequential submissions use the bounded single-worker executor. The dispatcher is `Closeable`, and closing it interrupts queued/running work through `shutdownNow`; long-running Rust operations additionally use the explicit cooperative `FfiCancellationToken` for application-level cancellation semantics.

This qualifies the Android dispatcher-policy portion of OYP-303. Full OYP-303 closeout still depends on completing the OYP-302 exported operation surface and qualifying those operations through generated bindings; this document does not claim that broader surface is complete.
