package top.easelink.lcg.network

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求限流与退避：保证最小请求间隔，并在命中风控/限流状态码后进入短暂封锁。
 *
 * 等待以协程 [delay] 挂起实现，不占用阻塞线程；调用方需在协程上下文中发起请求。
 */
internal object RateLimiter {
    private const val MIN_INTERVAL_MS = 600L
    private const val BACKOFF_BLOCK_MS = 8000L
    private val BACKOFF_CODES = setOf(403, 418, 429, 514)

    private val lastRequestAt = AtomicLong(0L)

    @Volatile
    private var blockedUntil = 0L

    /** 发起请求前等待：先处理退避封锁，再保证最小请求间隔。 */
    suspend fun await() {
        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) delay(waitForBlock)

        val since = System.currentTimeMillis() - lastRequestAt.get()
        if (since < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - since)
        lastRequestAt.set(System.currentTimeMillis())
    }

    /** 命中风控/限流状态码后进入短暂退避封锁。 */
    fun noteBlocked(statusCode: Int) {
        if (statusCode in BACKOFF_CODES) {
            blockedUntil = System.currentTimeMillis() + BACKOFF_BLOCK_MS
        }
    }
}
