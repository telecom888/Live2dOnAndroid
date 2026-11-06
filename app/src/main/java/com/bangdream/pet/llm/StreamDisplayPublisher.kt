package com.bangdream.pet.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Publishes cumulative display snapshots on the same dispatcher as the stream collector. */
internal class StreamDisplayPublisher<T : Any>(
    private val scope: CoroutineScope,
    private val blockOutput: Boolean,
    private val nanoTime: () -> Long = System::nanoTime,
    private val publish: (T) -> Unit,
) {
    private var pending: T? = null
    private var timer: Job? = null
    private var lastPublishedAt: Long? = null

    fun submit(value: T) {
        if (!blockOutput) {
            publish(value)
            return
        }
        pending = value
        val remaining = lastPublishedAt?.let { INTERVAL_NS - (nanoTime() - it) } ?: 0L
        if (remaining <= 0L) {
            flush()
        } else if (timer == null) {
            timer = scope.launch {
                delay((remaining + NS_PER_MS - 1) / NS_PER_MS)
                timer = null
                flush()
            }
        }
    }

    fun flush() {
        timer?.cancel()
        timer = null
        val value = pending ?: return
        pending = null
        lastPublishedAt = nanoTime()
        publish(value)
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
        const val INTERVAL_NS = 32L * NS_PER_MS
    }
}
