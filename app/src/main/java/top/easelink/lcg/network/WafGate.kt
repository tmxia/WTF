package top.easelink.lcg.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import top.easelink.lcg.utils.updateCookies

/**
 * 人机验证挂起闸门：等待内置浏览器 / 外部流程回灌放行 Cookie。
 *
 * 过渡期仍为同步阻塞等待（[runBlocking]），等待句柄集中在此组件内管理；
 * 后续调用链整体协程化后，可将 [awaitVerification] 改为 suspend 并以 `delay`/`await` 替代阻塞。
 */
internal object WafGate {
    private const val VERIFY_TIMEOUT_MS = 3 * 60 * 1000L

    private var pendingVerification: CompletableDeferred<String?>? = null

    /** 注册等待并阻塞直到验证完成 / 超时 / 取消；返回放行 Cookie，`null` 表示未通过。 */
    fun awaitVerification(): String? {
        val deferred = CompletableDeferred<String?>()
        synchronized(this) { pendingVerification = deferred }

        val verifiedCookie = runBlocking { withTimeoutOrNull(VERIFY_TIMEOUT_MS) { deferred.await() } }
        synchronized(this) { pendingVerification = null }
        return verifiedCookie
    }

    /** 验证流程回调：回灌 Cookie 并唤醒等待；空表示用户取消。 */
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

    /** 强制释放挂起的验证等待。 */
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
