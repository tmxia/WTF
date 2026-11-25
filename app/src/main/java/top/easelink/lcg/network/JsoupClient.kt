package top.easelink.lcg.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.framework.utils.APP_USER_AGENT
import top.easelink.lcg.BuildConfig
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.ui.main.model.SessionExpiredEvent
import top.easelink.lcg.ui.main.model.WafVerificationEvent
import top.easelink.lcg.ui.main.source.checkMessages
import top.easelink.lcg.ui.main.source.extractFormHash
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object JsoupClient : ApiRequest {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var formHash: String? = null
        set(value) { Timber.d("formHash = $value"); field = value }

    private val lastTime = AtomicLong(0L)
    private val CHECK_INTERVAL = if (BuildConfig.DEBUG) 60 * 1000 else 30 * 1000
    private val sessionUserAgent: String = APP_USER_AGENT
    private val sessionExpiredFiredOnce = AtomicBoolean(false)

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(LCGCookieJar.jar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", sessionUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Host", WebsiteConstant.SERVER_HOST)
                .header("Referer", SERVER_BASE_URL)
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(request)
        }
        .build()

    fun completeVerification(cookieHeader: String?) { WafGate.complete(cookieHeader) }

    @Throws(SocketTimeoutException::class, IOException::class)
    override suspend fun sendGetRequestWithQuery(query: String): Document = sendGetRequestWithUrl(
        // 站点 DOM 里的 href 可能是相对路径，也可能已经是绝对地址：
        // 直接无脑拼 SERVER_BASE_URL 会把绝对地址拼成非法 URL，点赞/取消收藏等写操作全部失败。
        if (query.startsWith("http://") || query.startsWith("https://")) query
        else if (query.startsWith("/")) SERVER_BASE_URL.trimEnd('/') + query
        else SERVER_BASE_URL + query,
        enableWafPopup = true
    )

    suspend fun sendAjaxRequest(query: String): String {
        val request = Request.Builder().url("$SERVER_BASE_URL$query").get().build()
        return okHttpClient.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override suspend fun sendGetRequestWithUrl(url: String): Document = sendGetRequestWithUrl(url, enableWafPopup = true)

    @Throws(SocketTimeoutException::class, IOException::class)
    suspend fun sendGetRequestWithUrl(url: String, enableWafPopup: Boolean): Document {
        var retryCount = 0
        var html: String
        while (true) {
            try {
                val request = Request.Builder().url(url).get().build()
                html = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 200) {
                        if (response.code == 412 || response.code == 403) return@use response.body?.string() ?: ""
                        throw IOException("HTTP ${response.code}")
                    }
                    response.body?.string() ?: ""
                }
                break
            } catch (e: SocketTimeoutException) {
                if (retryCount >= 2) throw e
                retryCount++
                kotlinx.coroutines.delay(1000L * retryCount)
            } catch (e: IOException) {
                if (retryCount >= 1) throw e
                retryCount++
                kotlinx.coroutines.delay(1000L)
            }
        }

        if (top.easelink.lcg.utils.WafSolver.isWafPage(html)) {
            if (!enableWafPopup || url.contains("home.php?mod=task")) throw NetworkException.WafBlocked("WAF_VERIFICATION_REQUIRED")
            EventBus.getDefault().post(WafVerificationEvent(url))
            if (WafGate.awaitVerification() != null) return sendGetRequestWithUrl(url, false)
            else throw NetworkException.WafBlocked("验证超时或被取消，无法绕过 WAF")
        }

        val doc = Jsoup.parse(html, url)
        checkResponse(doc)
        return doc
    }

    @Throws(SocketTimeoutException::class, IOException::class)
    override suspend fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Document = sendPostRequestWithUrl(url, form, enableWafPopup = true)

    suspend fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?, enableWafPopup: Boolean): Document {
        val builder = FormBody.Builder(charset("GBK"))
        form?.forEach { (k, v) -> builder.add(k, v) }
        
        var retryCount = 0
        var html: String
        while (true) {
            try {
                val request = Request.Builder().url(url).post(builder.build()).build()
                html = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 200) {
                        if (response.code == 412 || response.code == 403) return@use response.body?.string() ?: ""
                        throw IOException("HTTP ${response.code}")
                    }
                    response.body?.string() ?: ""
                }
                break
            } catch (e: SocketTimeoutException) {
                if (retryCount >= 2) throw e
                retryCount++
                kotlinx.coroutines.delay(1000L * retryCount)
            } catch (e: IOException) {
                if (retryCount >= 1) throw e
                retryCount++
                kotlinx.coroutines.delay(1000L)
            }
        }

        if (top.easelink.lcg.utils.WafSolver.isWafPage(html)) {
            if (!enableWafPopup || url.contains("home.php?mod=task")) throw NetworkException.WafBlocked("WAF_VERIFICATION_REQUIRED")
            EventBus.getDefault().post(WafVerificationEvent(url))
            if (WafGate.awaitVerification() != null) return sendPostRequestWithUrl(url, form, false)
            else throw NetworkException.WafBlocked("验证超时或被取消，无法绕过 WAF")
        }

        val doc = Jsoup.parse(html, url)
        checkResponse(doc)
        return doc
    }

    fun forceReleasePendingVerification() { WafGate.forceRelease() }
    fun resetSessionExpiredFlag() { sessionExpiredFiredOnce.set(false) }

    private fun checkResponse(doc: Document) {
        clientScope.launch {
            if (formHash.isNullOrEmpty()) formHash = extractFormHash(doc)
            val now = System.currentTimeMillis()
            if (now - lastTime.get() > CHECK_INTERVAL) {
                lastTime.set(now)
                try { checkLoginState(doc); checkMessages(doc) } catch (e: Exception) { Timber.e(e) }
            }
        }
    }

    private fun checkLoginState(doc: Document) {
        if (!UserDataRepo.isLoggedIn) return
        val hasMessageLogin = doc.getElementById("messagelogin") != null
        val messageText = doc.getElementById("messagetext")?.text().orEmpty()
        val explicitExpired = messageText.contains("您需要先登录") || messageText.contains("请先登录") || messageText.contains("登录已失效") || messageText.contains("尚未登录")
        if (!hasMessageLogin && !explicitExpired) { sessionExpiredFiredOnce.compareAndSet(true, false); return }
        if (sessionExpiredFiredOnce.compareAndSet(false, true)) EventBus.getDefault().post(SessionExpiredEvent())
    }
}
