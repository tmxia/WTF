package top.easelink.lcg.network

import java.util.concurrent.atomic.AtomicLong

/**
 * 请求限流与退避：保证最小请求间隔，并在命中风控/限流状态码后进入短暂封锁。
 *
 * 当前调用链仍是同步阻塞模型，等待以 `Thread.sleep` 实现；后续调用链协程化后可替换为 `delay`。
 */
internal object RateLimiter {
    private const val MIN_INTERVAL_MS = 300L
    private const val BACKOFF_BLOCK_MS = 3000L
    private val BACKOFF_CODES = setOf(403, 429, 514)

    private val lastRequestAt = AtomicLong(0L)

    @Volatile
    private var blockedUntil = 0L

    /** 发起请求前等待：先处理退避封锁，再保证最小请求间隔。 */
    @Synchronized
    fun await() {
        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) Thread.sleep(waitForBlock)

        val since = System.currentTimeMillis() - lastRequestAt.get()
        if (since < MIN_INTERVAL_MS) Thread.sleep(MIN_INTERVAL_MS - since)
        lastRequestAt.set(System.currentTimeMillis())
    }

    /** 命中风控/限流状态码后进入短暂退避封锁。 */
    fun noteBlocked(statusCode: Int) {
        if (statusCode in BACKOFF_CODES) {
            blockedUntil = System.currentTimeMillis() + BACKOFF_BLOCK_MS
        }
    }
}
