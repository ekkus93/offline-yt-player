package com.ekkus.offlineytplayer

import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

/**
 * Boundary for Rust/UniFFI calls that may block on persistence, source resolution, or downloads.
 *
 * UI code must submit blocking core work through this dispatcher rather than invoking it inline on
 * the Android main thread. The single worker also gives v1 a deliberately bounded core-call
 * concurrency policy until individual operations have stronger parallelism requirements.
 */
internal class CoreCallDispatcher(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(CoreThreadFactory),
) : Closeable {
    fun <T> submit(call: () -> T): Future<T> = executor.submit(Callable(call))

    override fun close() {
        executor.shutdownNow()
    }

    private object CoreThreadFactory : ThreadFactory {
        override fun newThread(runnable: Runnable): Thread = Thread(runnable, "offline-yt-core-worker").apply {
            isDaemon = true
        }
    }
}
