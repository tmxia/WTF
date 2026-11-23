package top.easelink.lcg.network

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong






internal object RateLimiter {
    private const val MIN_INTERVAL_MS = 600L
    private const val BACKOFF_BLOCK_MS = 8000L
    private val BACKOFF_CODES = setOf(403, 418, 429, 514)

    private val lastRequestAt = AtomicLong(0L)

    @Volatile
    private var blockedUntil = 0L

    
    suspend fun await() {
        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) delay(waitForBlock)

        val since = System.currentTimeMillis() - lastRequestAt.get()
        if (since < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - since)
        lastRequestAt.set(System.currentTimeMillis())
    }

    
    fun noteBlocked(statusCode: Int) {
        if (statusCode in BACKOFF_CODES) {
            blockedUntil = System.currentTimeMillis() + BACKOFF_BLOCK_MS
        }
    }
}
