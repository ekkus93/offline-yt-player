package com.ekkus.offlineytplayer.downloads

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns one in-process foreground native writer. A service timeout or destruction
 * permanently closes this instance to new work and cooperatively stops the
 * current UniFFI transfer. Stop and begin racing invoke the stop callback at
 * most once; the native worker performs durable retryable settlement.
 */
internal class ForegroundWorkerOwnership {
    private val stopped = AtomicBoolean(false)
    private val activeStop = AtomicReference<(() -> Unit)?>(null)

    val isStopped: Boolean
        get() = stopped.get()

    fun tryBegin(stopWorker: () -> Unit): Boolean {
        if (stopped.get()) return false
        if (!activeStop.compareAndSet(null, stopWorker)) return false
        if (stopped.get()) {
            activeStop.getAndSet(null)?.invoke()
            return false
        }
        return true
    }

    fun finish(stopWorker: () -> Unit) {
        activeStop.compareAndSet(stopWorker, null)
    }

    fun stop() {
        stopped.set(true)
        activeStop.getAndSet(null)?.invoke()
    }
}
