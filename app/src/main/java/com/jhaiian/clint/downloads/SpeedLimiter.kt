package com.jhaiian.clint.downloads

/**
 * Token-bucket throttle enforcing a maximum aggregate transfer rate for one download. A single
 * instance is shared across every part of a parallel download so splitting into more parts
 * increases concurrency, never the total throughput.
 */
internal class SpeedLimiter(initialLimitBytesPerSec: Long) {

    @Volatile
    private var limitBytesPerSec: Long = initialLimitBytesPerSec

    private var availableTokens = 0.0
    private var lastRefillNanos = System.nanoTime()
    private val lock = Any()

    /**
     * Changes the enforced rate on a limiter already in use by an active transfer; takes effect
     * on the very next [acquire] call rather than requiring the download to restart. Clamps any
     * already-accumulated tokens down to the new ceiling so a lowered limit is honored right
     * away instead of letting one large burst through first.
     */
    fun updateLimit(newLimitBytesPerSec: Long) {
        synchronized(lock) {
            limitBytesPerSec = newLimitBytesPerSec
            if (newLimitBytesPerSec > 0L) {
                availableTokens = minOf(availableTokens, newLimitBytesPerSec.toDouble())
            }
        }
    }

    /**
     * Blocks the calling thread until [bytes] worth of transfer is allowed under the configured
     * rate. No-op when unlimited ([limitBytesPerSec] <= 0), so callers can call this unconditionally.
     */
    fun acquire(bytes: Int) {
        if (bytes <= 0) return
        synchronized(lock) {
            if (limitBytesPerSec <= 0L) return
            var remaining = bytes.toDouble()
            while (remaining > 0.0) {
                val limit = limitBytesPerSec
                if (limit <= 0L) return
                val now = System.nanoTime()
                val elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0
                lastRefillNanos = now
                availableTokens = minOf(limit.toDouble(), availableTokens + elapsedSeconds * limit)
                if (availableTokens >= remaining) {
                    availableTokens -= remaining
                    remaining = 0.0
                } else {
                    remaining -= availableTokens
                    availableTokens = 0.0
                    val waitMs = ((remaining / limit) * 1000.0).toLong().coerceAtLeast(1L)
                    Thread.sleep(waitMs)
                }
            }
        }
    }
}
