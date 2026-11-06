package com.tj90.unduanki.study

/** Measures question-and-answer time, excluding any time the app spent in the background. */
class ForegroundClock(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var active = false
    private var accumulated = 0L
    private var startedAt: Long? = null

    fun start() { active = true; accumulated = 0; startedAt = now() }
    fun pause() { startedAt?.let { accumulated += now() - it }; startedAt = null }
    fun resume() { if (active && startedAt == null) startedAt = now() }

    fun elapsedMs(): Long = (accumulated + (startedAt?.let { now() - it } ?: 0L)).coerceAtLeast(0)

    /** Stops timing and returns the elapsed foreground time. */
    fun finish(): Long { val elapsed = elapsedMs(); active = false; startedAt = null; accumulated = 0; return elapsed }
}
