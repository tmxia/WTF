package top.easelink.lcg.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.BuildConfig
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.ui.main.model.SessionExpiredEvent
import top.easelink.lcg.ui.main.model.WafVerificationEvent
import top.easelink.lcg.ui.main.source.checkMessages
import top.easelink.lcg.ui.main.source.extractFormHash
import top.easelink.framework.utils.APP_USER_AGENT
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.getCookiesFor
import top.easelink.lcg.utils.updateCookies
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

object JsoupClient : ApiRequest {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val FOLLOW_REDIRECTS_ENABLE = true

    var formHash: String? = null
        set(value) { Timber.d("formHash = $value"); field = value }

    private val lastTime = java.util.concurrent.atomic.AtomicLong(0L)
    private val CHECK_INTERVAL = if (BuildConfig.DEBUG) 60 * 1000 else 30 * 1000
    private const val TIME_OUT_LIMIT = 30 * 1000
    private const val BASE_URL = SERVER_BASE_URL
    
    private val sessionUserAgent: String = APP_USER_AGENT

    private val sessionExpiredFiredOnce = AtomicBoolean(false)

    fun completeVerification(cookieHeader: String?) {
        WafGate.complete(cookieHeader)
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override suspend fun sendGetRequestWithQuery(query: String): Document = sendGetRequestWithUrl("$BASE_URL$query", enableWafPopup = true)

    suspend fun sendAjaxRequest(query: String): String {
        val url = "$BASE_URL$query"
        return getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(false)
            .execute()
            .let { updateCookies(it.cookies()); it.body() }
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override suspend fun sendGetRequestWithUrl(url: String): Document = sendGetRequestWithUrl(url, enableWafPopup = true)

    @Throws(SocketTimeoutException::class, IOException::class)
    suspend fun sendGetRequestWithUrl(url: String, enableWafPopup: Boolean): Document {
        var response = getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(FOLLOW_REDIRECTS_ENABLE)
            .execute()

        updateCookies(response.cookies())

        // [ARCH-PHASE-2] 统一处理 WAF 拦截与重发
        response = handleWafChallenge(url, response, enableWafPopup) {
            getConnection(url)
                .method(Connection.Method.GET)
                .followRedirects(FOLLOW_REDIRECTS_ENABLE)
                .execute()
        }

        RateLimiter.noteBlocked(response.statusCode())
        return response.parse().also { checkResponse(it) }
    }

    override suspend fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Connection.Response = sendPostRequestWithUrl(url, form, enableWafPopup = true)

    suspend fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?, enableWafPopup: Boolean): Connection.Response {
        var response = getConnection(url)
            .apply { if (form != null) data(form) }
            .postDataCharset("gbk")
            .method(Connection.Method.POST)
            .execute()

        updateCookies(response.cookies())

        // [ARCH-PHASE-2] 统一处理 WAF 拦截与重发
        response = handleWafChallenge(url, response, enableWafPopup) {
            getConnection(url)
                .apply { if (form != null) data(form) }
                .postDataCharset("gbk")
                .method(Connection.Method.POST)
                .execute()
        }

        RateLimiter.noteBlocked(response.statusCode())
        return response
    }

    private suspend fun handleWafChallenge(
        url: String,
        response: Connection.Response,
        enableWafPopup: Boolean,
        retryRequest: suspend () -> Connection.Response
    ): Connection.Response {
        if (!isWafPage(response.body())) return response

        if (!enableWafPopup || url.contains("home.php?mod=task")) {
            throw NetworkException.WafBlocked("WAF_VERIFICATION_REQUIRED")
        }

        Timber.w("网络层：命中高级 JS WAF 盾，触发 URL: $url")
        EventBus.getDefault().post(WafVerificationEvent(url))

        val verifiedCookie = WafGate.awaitVerification()
        if (verifiedCookie != null) {
            Timber.d("真人验证通过，重新发起业务请求...")
            val newResponse = retryRequest()
            updateCookies(newResponse.cookies())
            return newResponse
        } else {
            Timber.e("网络层：真人验证超时或取消")
            throw NetworkException.WafBlocked("验证超时或被取消，无法绕过 WAF")
        }
    }

private fun isWafPage(html: String?): Boolean {
        // [ARCH-PHASE-1] 直接调用 WafSolver，不再维护两套逻辑
        return top.easelink.lcg.utils.WafSolver.isWafPage(html)
    }

    private suspend fun getConnection(url: String): Connection {
        RateLimiter.await()

        val isLcgHost = url.contains(WebsiteConstant.SERVER_HOST)
        return Jsoup.connect(url)
            .timeout(TIME_OUT_LIMIT)
            .ignoreHttpErrors(true)
            .cookies(getCookiesFor(url))
            .userAgent(sessionUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Host", WebsiteConstant.SERVER_HOST)
            .apply { if (isLcgHost) header("Referer", WebsiteConstant.SERVER_BASE_URL) }
            .header("Upgrade-Insecure-Requests", "1")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "Windows")
    }

    fun forceReleasePendingVerification() {
        WafGate.forceRelease()
    }

    fun resetSessionExpiredFlag() { sessionExpiredFiredOnce.set(false) }

    private fun checkResponse(doc: Document) {
        clientScope.launch {
            if (formHash.isNullOrEmpty()) formHash = extractFormHash(doc)
            val now = System.currentTimeMillis()
                if (now - lastTime.get() > CHECK_INTERVAL) {
                    lastTime.set(now)
                try {
                    checkLoginState(doc); checkMessages(doc)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    private fun checkLoginState(doc: Document) {
        if (!UserDataRepo.isLoggedIn) return
        val hasMessageLogin = doc.getElementById("messagelogin") != null
        val messageText = doc.getElementById("messagetext")?.text().orEmpty()
        // [ARCH-PHASE-4] 增强容错匹配，不再依赖单一的死板字符串
        val explicitExpired = messageText.contains("您需要先登录") || 
                              messageText.contains("请先登录") || 
                              messageText.contains("登录已失效") || 
                              messageText.contains("尚未登录")

        if (!hasMessageLogin && !explicitExpired) {
            sessionExpiredFiredOnce.compareAndSet(true, false)
            return
        }

        if (sessionExpiredFiredOnce.compareAndSet(false, true)) {
            EventBus.getDefault().post(SessionExpiredEvent())
        }
    }

    // private const val SESSION_EXPIRED_MARKER = " 您需要先登录才能继续" // 已在匹配逻辑中内联，弃用
}
