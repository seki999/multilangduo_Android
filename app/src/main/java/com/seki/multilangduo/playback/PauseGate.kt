package com.seki.multilangduo.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Suspend work and countdowns without cancelling the enclosing playback task. */
class PauseGate(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val paused = MutableStateFlow(false)
    val isPaused get() = paused.value
    fun pause() { paused.value = true }
    fun resume() { paused.value = false }
    suspend fun awaitResumed() { paused.first { !it } }
    suspend fun delayActive(milliseconds: Long) {
        require(milliseconds >= 0)
        var remaining = milliseconds
        while (remaining > 0) {
            awaitResumed()
            val started = nowMillis()
            val interrupted = withTimeoutOrNull(remaining) { paused.first { it }; true } ?: false
            if (!interrupted) remaining = 0
            else remaining = (remaining - (nowMillis() - started).coerceAtLeast(0)).coerceAtLeast(0)
        }
        awaitResumed()
    }
}
