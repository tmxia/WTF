package top.easelink.lcg.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import top.easelink.lcg.utils.updateCookies







internal object WafGate {
    private const val VERIFY_TIMEOUT_MS = 3 * 60 * 1000L

    private var pendingVerification: CompletableDeferred<String?>? = null

    
    suspend fun awaitVerification(): String? {
        val deferred = CompletableDeferred<String?>()
        synchronized(this) { pendingVerification = deferred }

        val verifiedCookie = try {
            withTimeout(VERIFY_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Timber.w("人机验证等待超时")
            null
        }
        synchronized(this) { pendingVerification = null }
        return verifiedCookie
    }

    
    fun complete(cookieHeader: String?) {
        val deferred = synchronized(this) { pendingVerification }
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
        Timber.d("真人验证完成，Cookie 已回灌并唤醒网络层")
    }

    
    fun forceRelease() {
        try {
            synchronized(this) {
                pendingVerification?.complete(null)
                pendingVerification = null
            }
            Timber.d("强制释放挂起的 WAF 验证")
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
