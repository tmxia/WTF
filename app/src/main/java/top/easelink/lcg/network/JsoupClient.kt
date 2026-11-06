package top.easelink.lcg.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.ui.main.model.SessionExpiredEvent
import top.easelink.lcg.ui.main.source.checkMessages
import top.easelink.lcg.ui.main.source.extractFormHash
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.getCookiesFor
import top.easelink.lcg.utils.updateCookies
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private const val MIN_INTERVAL_MS = 400L
    private val lastRequestAt = AtomicLong(0L)
    private val BACKOFF_CODES = setOf(403, 429, 514)
    @Volatile private var blockedUntil = 0L

    @Throws(SocketTimeoutException::class, IOException::class)
    override fun sendGetRequestWithQuery(query: String): Document = sendGetRequestWithUrl("$BASE_URL$query")

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
        var response = getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(FOLLOW_REDIRECTS_ENABLE)
            .execute()

        // 核心终极修复：如果命中盾，不要用 WebView 渲染，而是用 WebView Fetch 获取原始 HTML
        if (isWafPage(response.body())) {
            Timber.w("网络层：命中高级 JS WAF 盾，启动后台 WebView Fetch 获取数据...")
            val rawHtml = silentWebViewFetch(url)
            
            if (rawHtml != null) {
                Timber.d("网络层：WebView Fetch 成功，返回原始 HTML 长度: ${rawHtml.length}")
                return Jsoup.parse(rawHtml, url).also { checkResponse(it) }
            } else {
                Timber.e("网络层：WebView Fetch 失败")
                throw IOException("WAF 盾未能成功绕过，请稍后再试")
            }
        }

        updateCookies(response.cookies())
        if (response.statusCode() in BACKOFF_CODES) blockedUntil = System.currentTimeMillis() + 3000
        return response.parse().also { checkResponse(it) }
    }

    override fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Connection.Response {
        var response = getConnection(url)
            .apply { if (form != null) data(form) }
            .postDataCharset("gbk")
            .method(Connection.Method.POST)
            .execute()

        if (isWafPage(response.body())) {
            throw IOException("WAF 盾未能成功绕过 (POST 暂不支持自动过盾)")
        }

        updateCookies(response.cookies())
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

    /**
     * 终极必杀技：在 WebView 内直接发起 fetch 请求，彻底免疫 WAF
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun silentWebViewFetch(targetUrl: String): String? {
        val latch = CountDownLatch(1)
        var finalHtml: String? = null

        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(LCGApp.context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = DESKTOP_UA
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    val currentCookies = getCookiesFor(BASE_URL)
                    val cookieStr = currentCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    if (cookieStr.isNotEmpty()) {
                        cookieManager.setCookie(BASE_URL, cookieStr)
                    }

                    // 核心修复：在 WebView 创建时立刻注入接口，确保 JS 能随时调用
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun fetchResult(base64Str: String) {
                            try {
                                finalHtml = String(Base64.decode(base64Str, Base64.DEFAULT), Charsets.UTF_8)
                                Timber.d("WebView fetch 成功获取数据，Base64 解码完毕")
                            } catch (e: Exception) {
                                Timber.e(e, "解析 Base64 异常")
                            }
                            CookieManager.getInstance().flush()
                            updateLatestCookies()
                            post { destroy() }
                            latch.countDown()
                        }

                        @android.webkit.JavascriptInterface
                        fun fetchError(error: String) {
                            Timber.e("WebView fetch 失败: $error")
                            post { destroy() }
                            latch.countDown()
                        }
                    }, "hook")
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 页面加载完，延迟 2.5 秒确保 WAF 盾执行完并刷新 Cookie
                            Handler(Looper.getMainLooper()).postDelayed({
                                // 使用 fetch 请求目标 URL
                                val js = """
                                    (function() {
                                        fetch('$targetUrl', { credentials: 'include' })
                                        .then(response => response.text())
                                        .then(text => btoa(unescape(encodeURIComponent(text))))
                                        .then(b64 => window.hook.fetchResult(b64))
                                        .catch(e => window.hook.fetchError(e.toString()));
                                    })()
                                """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }, 2500L)
                        }
                    }
                    loadUrl(BASE_URL)
                }
            } catch (e: Exception) {
                Timber.e(e, "静默 WebView 发生异常")
                latch.countDown()
            }
        }

        try {
            latch.await(20, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Timber.e(e, "静默 WebView 等待超时")
        }
        return finalHtml
    }

    private fun updateLatestCookies() {
        val latestCookie = CookieManager.getInstance().getCookie(BASE_URL)
        if (!latestCookie.isNullOrEmpty()) {
            val cookies = mutableMapOf<String, String>()
            for (pair in latestCookie.split(";")) {
                val eq = pair.indexOf('=')
                if (eq > 0) cookies[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
            }
            updateCookies(cookies)
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
