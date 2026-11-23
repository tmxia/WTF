package top.easelink.lcg.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import top.easelink.lcg.utils.updateCookies

internal object WafGate {
    private const val VERIFY_TIMEOUT_MS = 3 * 60 * 1000L
    private var pendingVerification: CompletableDeferred<String?>? = null
    private val lock = Any()

    suspend fun awaitVerification(): String? {
        // [ARCH-PHASE-8] 并发防抖：如果已经有协程在等待，直接复用，避免被后来的请求覆盖
        val deferred = synchronized(lock) {
            val existing = pendingVerification
            if (existing != null && !existing.isCompleted) {
                existing
            } else {
                CompletableDeferred<String?>().also { pendingVerification = it }
            }
        }

        return try {
            withTimeout(VERIFY_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Timber.w("人机验证等待超时")
            synchronized(lock) {
                if (pendingVerification === deferred) {
                    pendingVerification = null
                }
            }
            // 通知所有共享这个 deferred 的协程超时
            deferred.complete(null)
            null
        }
    }

    fun complete(cookieHeader: String?) {
        val deferred = synchronized(lock) {
            val d = pendingVerification
            pendingVerification = null // 提前清理，允许后续请求重新触发验证
            d
        }
        
        if (cookieHeader.isNullOrBlank()) {
            deferred?.complete(null)
            Timber.w("用户取消了人机验证")
            return
        }

        val cookies = mutableMapOf<String, String>()
        for (pair in cookieHeader.split(";")) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val name = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
            }
        }
        updateCookies(cookies)
        deferred?.complete(cookieHeader)
        Timber.d("真人验证完成，Cookie 已回灌并唤醒所有等待的网络层协程")
    }

    fun forceRelease() {
        try {
            synchronized(lock) {
                pendingVerification?.complete(null)
                pendingVerification = null
            }
            Timber.d("强制释放挂起的 WAF 验证")
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
