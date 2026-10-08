package com.ekkus.offlineytplayer

/**
 * Rejects repository UI publications queued before an Activity stop or recreation.
 *
 * Capture the token before blocking repository/FFI work, then verify it on the UI
 * thread immediately before applying state. A new start invalidates prior tokens.
 */
internal class LifecyclePublicationGate {
    private var generation = 0L
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        generation += 1
        started = true
    }

    @Synchronized
    fun stop() {
        if (!started) return
        generation += 1
        started = false
    }

    @Synchronized
    fun capture(): Long? = if (started) generation else null

    @Synchronized
    fun permits(token: Long): Boolean = started && token == generation
}
