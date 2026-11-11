package top.easelink.lcg.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.getCookiesFor
import top.easelink.lcg.utils.updateCookies
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object JsoupClient : ApiRequest {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val FOLLOW_REDIRECTS_ENABLE = true

    var formHash: String? = null
        set(value) { Timber.d("formHash = $value"); field = value }

    private var lastTime = 0L
    private val CHECK_INTERVAL = if (BuildConfig.DEBUG) 60 * 1000 else 30 * 1000
    private const val TIME_OUT_LIMIT = 30 * 1000
    private const val BASE_URL = SERVER_BASE_URL
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val sessionExpiredFiredOnce = AtomicBoolean(false)
    private const val MIN_INTERVAL_MS = 300L 
    private val lastRequestAt = AtomicLong(0L)
    private val BACKOFF_CODES = setOf(403, 429, 514)
    @Volatile private var blockedUntil = 0L

    
    private var pendingVerification: CompletableDeferred<String?>? = null
    private const val VERIFY_TIMEOUT_MS = 3 * 60 * 1000L

    fun completeVerification(cookieHeader: String?) {
        val deferred = synchronized(this) { pendingVerification }
        if (cookieHeader.isNullOrBlank()) {
            deferred?.complete(null)
            Timber.w("用户取消了人机验证")
        } else {
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
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override fun sendGetRequestWithQuery(query: String): Document = sendGetRequestWithUrl("$BASE_URL$query", enableWafPopup = true)

    fun sendAjaxRequest(query: String): String {
        val url = "$BASE_URL$query"
        return getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(false)
            .execute()
            .let { updateCookies(it.cookies()); it.body() }
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override fun sendGetRequestWithUrl(url: String): Document {
        return sendGetRequestWithUrl(url, enableWafPopup = true)
    }

    


    @Throws(SocketTimeoutException::class, IOException::class)
    fun sendGetRequestWithUrl(url: String, enableWafPopup: Boolean): Document {
        var response = getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(FOLLOW_REDIRECTS_ENABLE)
            .execute()

        updateCookies(response.cookies())

        if (isWafPage(response.body())) {
            
            if (!enableWafPopup || url.contains("home.php?mod=task")) {
                throw IOException("WAF_VERIFICATION_REQUIRED")
            }
            
            Timber.w("网络层：命中高级 JS WAF 盾，触发 URL: $url")
            EventBus.getDefault().post(WafVerificationEvent(url))
            
            val deferred = CompletableDeferred<String?>()
            synchronized(this) { pendingVerification = deferred }
            
            val verifiedCookie = runBlocking { withTimeoutOrNull(VERIFY_TIMEOUT_MS) { deferred.await() } }
            synchronized(this) { pendingVerification = null }

            if (verifiedCookie != null) {
                Timber.d("真人验证通过，重新发起业务请求...")
                response = getConnection(url)
                    .method(Connection.Method.GET)
                    .followRedirects(FOLLOW_REDIRECTS_ENABLE)
                    .execute()
                updateCookies(response.cookies())
            } else {
                Timber.e("网络层：真人验证超时或取消")
                throw IOException("验证超时或被取消，无法绕过 WAF")
            }
        }

        if (response.statusCode() in BACKOFF_CODES) blockedUntil = System.currentTimeMillis() + 3000
        return response.parse().also { checkResponse(it) }
    }

    override fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Connection.Response {
        return sendPostRequestWithUrl(url, form, enableWafPopup = true)
    }

    fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?, enableWafPopup: Boolean): Connection.Response {
        var response = getConnection(url)
            .apply { if (form != null) data(form) }
            .postDataCharset("gbk")
            .method(Connection.Method.POST)
            .execute()

        updateCookies(response.cookies())

        if (isWafPage(response.body())) {
            
            if (!enableWafPopup || url.contains("home.php?mod=task")) {
                throw IOException("WAF_VERIFICATION_REQUIRED")
            }
            Timber.w("网络层：POST 命中 WAF 盾，触发 URL: $url")
            EventBus.getDefault().post(WafVerificationEvent(url))
            
            val deferred = CompletableDeferred<String?>()
            synchronized(this) { pendingVerification = deferred }
            
            val verifiedCookie = runBlocking { withTimeoutOrNull(VERIFY_TIMEOUT_MS) { deferred.await() } }
            synchronized(this) { pendingVerification = null }

            if (verifiedCookie != null) {
                response = getConnection(url)
                    .apply { if (form != null) data(form) }
                    .postDataCharset("gbk")
                    .method(Connection.Method.POST)
                    .execute()
                updateCookies(response.cookies())
            } else {
                throw IOException("验证超时或被取消，无法绕过 WAF")
            }
        }

        if (response.statusCode() in BACKOFF_CODES) blockedUntil = System.currentTimeMillis() + 3000
        return response
    }

    private fun isWafPage(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return html.contains("wzwsquestion") || html.contains("wzws-waf-cgi") || html.contains("waf_zw_verify")
    }

    private fun getConnection(url: String): Connection {
        val waitForBlock = blockedUntil - System.currentTimeMillis()
        if (waitForBlock > 0) Thread.sleep(waitForBlock)

        synchronized(this) {
            val since = System.currentTimeMillis() - lastRequestAt.get()
            if (since < MIN_INTERVAL_MS) Thread.sleep(MIN_INTERVAL_MS - since)
            lastRequestAt.set(System.currentTimeMillis())
        }

        return Jsoup.connect(url)
            .timeout(TIME_OUT_LIMIT)
            .ignoreHttpErrors(true)
            .cookies(getCookiesFor(url))
            .userAgent(DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Host", WebsiteConstant.SERVER_HOST)
            .header("Upgrade-Insecure-Requests", "1")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "Windows")
    }

    
    fun forceReleasePendingVerification() {
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

    fun resetSessionExpiredFlag() { sessionExpiredFiredOnce.set(false) }

    private fun checkResponse(doc: Document) {
        clientScope.launch {
            if (formHash.isNullOrEmpty()) formHash = extractFormHash(doc)
            if (System.currentTimeMillis() - lastTime > CHECK_INTERVAL) {
                lastTime = System.currentTimeMillis()
                try { checkLoginState(doc); checkMessages(doc) } catch (e: Exception) { Timber.e(e) }
            }
        }
    }

    private fun checkLoginState(doc: Document) {
        if (!UserDataRepo.isLoggedIn) return
        val hasMessageLogin = doc.getElementById("messagelogin") != null
        val messageText = doc.getElementById("messagetext")?.text().orEmpty()
        val explicitExpired = messageText.contains(SESSION_EXPIRED_MARKER)

        if (!hasMessageLogin && !explicitExpired) {
            sessionExpiredFiredOnce.compareAndSet(true, false)
            return
        }

        if (sessionExpiredFiredOnce.compareAndSet(false, true)) {
            EventBus.getDefault().post(SessionExpiredEvent())
        }
    }

    private const val SESSION_EXPIRED_MARKER = " 您需要先登录才能继续"
}
