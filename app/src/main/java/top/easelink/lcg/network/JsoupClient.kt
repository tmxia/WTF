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
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.ui.main.model.SessionExpiredEvent
import top.easelink.lcg.ui.main.source.checkMessages
import top.easelink.lcg.ui.main.source.extractFormHash
import top.easelink.lcg.utils.WafSolver
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.getCookiesFor
import top.easelink.lcg.utils.getDeviceUserAgent
import top.easelink.lcg.utils.updateCookies
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
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

        if (WafSolver.isWafPage(response.body())) {
            Timber.w("网络层：命中 WAF 盾，自动启动纯算法求解...")
            if (bypassWaf(url, response.body())) {
                response = getConnection(url)
                    .method(Connection.Method.GET)
                    .followRedirects(FOLLOW_REDIRECTS_ENABLE)
                    .execute()
            }
        }

        updateCookies(response.cookies())
        return response.parse().also { checkResponse(it) }
    }

    override fun sendPostRequestWithUrl(url: String, form: MutableMap<String, String>?): Connection.Response {
        var response = getConnection(url)
            .apply { if (form != null) data(form) }
            .postDataCharset("gbk")
            .method(Connection.Method.POST)
            .execute()

        if (WafSolver.isWafPage(response.body())) {
            Timber.w("网络层：POST 命中 WAF 盾，自动启动纯算法求解...")
            if (bypassWaf(url, response.body())) {
                response = getConnection(url)
                    .apply { if (form != null) data(form) }
                    .postDataCharset("gbk")
                    .method(Connection.Method.POST)
                    .execute()
            }
        }

        updateCookies(response.cookies())
        return response
    }

    fun resetSessionExpiredFlag() { sessionExpiredFiredOnce.set(false) }

    private fun getConnection(url: String): Connection {
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

    private fun bypassWaf(currentUrl: String, html: String): Boolean {
        val params = WafSolver.extractWafParams(html) ?: return false
        val (question, factorStr, base64Chars, api) = params
        val factor = factorStr.toInt()
        val timestamp = System.currentTimeMillis()
        val answer = WafSolver.calcAnswer(question, factor)
        val jsonStr = WafSolver.buildFpPayload(timestamp, answer, DESKTOP_UA)
        val encodedData = WafSolver.encodeCustomBase64(jsonStr.toByteArray(Charsets.UTF_8), base64Chars)

        val apiUrl = if (api.startsWith("/")) "${BASE_URL.removeSuffix("/")}$api" else api
        Timber.d("算法破盾：提交验证请求至 $apiUrl")

        for (attempt in 1..3) {
            try {
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("User-Agent", DESKTOP_UA)
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                conn.setRequestProperty("Referer", currentUrl)
                conn.setRequestProperty("Origin", BASE_URL.removeSuffix("/"))
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                conn.setRequestProperty("Sec-Fetch-Dest", "empty")
                conn.setRequestProperty("Sec-Fetch-Mode", "cors")
                conn.setRequestProperty("Sec-Fetch-Site", "same-origin")
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                conn.setRequestProperty("sec-ch-ua-mobile", "?0")
                conn.setRequestProperty("sec-ch-ua-platform", "Windows")

                val cookieStr = getCookiesFor(BASE_URL).entries.joinToString("; ") { "${it.key}=${it.value}" }
                if (cookieStr.isNotEmpty()) conn.setRequestProperty("Cookie", cookieStr)

                conn.doOutput = true
                conn.outputStream.use { it.write(encodedData.toByteArray(Charsets.UTF_8)) }

                val responseCode = conn.responseCode
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }

                Timber.d("算法破盾响应 (第${attempt}次)：code=$responseCode, body=${responseBody.take(80)}")

                if (responseCode == 200) {
                    if (responseBody.contains("waf_zw_verify") || responseBody.contains("Please enable JavaScript")) {
                        Thread.sleep(1000)
                        continue
                    }
                    val setCookieHeaders = conn.headerFields["Set-Cookie"]
                    if (setCookieHeaders != null) {
                        val newCookies = mutableMapOf<String, String>()
                        for (cookie in setCookieHeaders) {
                            val parts = cookie.split(";")[0].split("=", limit = 2)
                            if (parts.size == 2) newCookies[parts[0]] = parts[1]
                        }
                        if (newCookies.isNotEmpty()) {
                            updateCookies(newCookies)
                            Timber.d("算法破盾：成功同步新 Cookie")
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "算法破盾第 ${attempt} 次尝试异常")
            }
        }
        return false
    }

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
