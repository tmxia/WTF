package top.easelink.lcg.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

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
import top.easelink.lcg.utils.getDeviceUserAgent
import top.easelink.lcg.utils.updateCookies
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object JsoupClient : ApiRequest {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val FOLLOW_REDIRECTS_ENABLE = true

    var formHash: String? = null
        set(value) {
            Timber.d("formHash = $value")
            field = value
        }

    private var lastTime = 0L
    private val CHECK_INTERVAL = if (BuildConfig.DEBUG) 60 * 1000 else 30 * 1000

    
    private const val TIME_OUT_LIMIT = 30 * 1000
    private const val BASE_URL = SERVER_BASE_URL
    private val USER_AGENT = getDeviceUserAgent(LCGApp.context)

    



    private val sessionExpiredFiredOnce = AtomicBoolean(false)

    
    private const val MIN_INTERVAL_MS = 400L
    private val lastRequestAt = AtomicLong(0L)
    

    @Throws(SocketTimeoutException::class, IOException::class)
    override fun sendGetRequestWithQuery(query: String): Document {
        val url = "$BASE_URL$query"
        return getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(FOLLOW_REDIRECTS_ENABLE)
            .execute()
            .let {
                updateCookies(it.cookies())
                it.parse().also { doc ->
                    checkResponse(doc)
                }
            }
    }

    fun sendAjaxRequest(query: String): String {
        val url = "$BASE_URL$query"
        return getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(false)
            .execute()
            .let {
                updateCookies(it.cookies())
                it.body()
            }
    }

    override fun sendGetRequestWithUrl(url: String): Document {
        return getConnection(url)
            .method(Connection.Method.GET)
            .followRedirects(FOLLOW_REDIRECTS_ENABLE)
            .execute()
            .let {
                updateCookies(it.cookies())
                it.parse().also { doc ->
                    checkResponse(doc)
                }
            }
    }

    override fun sendPostRequestWithUrl(
        url: String,
        form: MutableMap<String, String>?
    ): Connection.Response {
        return getConnection(url)
            .apply {
                if (form != null) {
                    data(form)
                }
            }
            .postDataCharset("gbk")
            .method(Connection.Method.POST)
            .execute()
            .also {
                updateCookies(it.cookies())
            }
    }

    
    fun resetSessionExpiredFlag() {
        sessionExpiredFiredOnce.set(false)
    }

    private fun getConnection(url: String): Connection {
        
        synchronized(this) {
            val since = System.currentTimeMillis() - lastRequestAt.get()
            if (since < MIN_INTERVAL_MS) {
                Thread.sleep(MIN_INTERVAL_MS - since)
            }
            lastRequestAt.set(System.currentTimeMillis())
        }
        return Jsoup.connect(url)
            .timeout(TIME_OUT_LIMIT)
            .ignoreHttpErrors(true)
            .cookies(getCookiesFor(url))
            .userAgent(USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Cache-Control", "max-age=0")
            .header("Connection", "keep-alive")
            .header("Host", WebsiteConstant.SERVER_HOST)
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "Android")
    }

    private fun checkResponse(doc: Document) {
        clientScope.launch {
            if (formHash.isNullOrEmpty()) {
                formHash = extractFormHash(doc)
            }
            if (System.currentTimeMillis() - lastTime > CHECK_INTERVAL) {
                lastTime = System.currentTimeMillis()
                try {
                    checkLoginState(doc)
                    checkMessages(doc)
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
        val explicitExpired = messageText.contains(SESSION_EXPIRED_MARKER)

        if (!hasMessageLogin && !explicitExpired) {
            
            sessionExpiredFiredOnce.compareAndSet(true, false)
            return
        }

        Timber.w(
            "session expired detected: hasMessageLogin=%s explicitExpired=%s messageText='%s'",
            hasMessageLogin,
            explicitExpired,
            messageText.take(120)
        )
        if (sessionExpiredFiredOnce.compareAndSet(false, true)) {
            
            Timber.w("检测到风控拦截，启动静默 WebView 刷新 Cookie...")
            refreshCookieSilently {
                
                resetSessionExpiredFlag()
            }
            
        }
    }

    private const val SESSION_EXPIRED_MARKER = "您需要先登录才能继续"

    
    @SuppressLint("SetJavaScriptEnabled")
    private fun refreshCookieSilently(onComplete: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(LCGApp.context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = getDeviceUserAgent(LCGApp.context)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    val currentCookies = getCookiesFor(SERVER_BASE_URL)
                    val cookieStr = currentCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    CookieManager.getInstance().setCookie(SERVER_BASE_URL, cookieStr)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.contains(WebsiteConstant.SERVER_HOST) == true) {
                                
                                LCGCookieJar.syncFromWebView(SERVER_BASE_URL)
                                Timber.d("静默过盾完成，已同步最新 Cookie 到 JsoupClient")
                                view?.destroy()
                                onComplete()
                            }
                        }
                    }
                    loadUrl(SERVER_BASE_URL)
                }
            } catch (e: Exception) {
                Timber.e(e, "静默刷新 WebView 异常")
                onComplete() 
            }
        }
    }
    

}
