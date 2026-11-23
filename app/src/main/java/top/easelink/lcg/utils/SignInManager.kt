package top.easelink.lcg.utils

import android.view.View

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.LCGCookieJar
import java.util.concurrent.atomic.AtomicBoolean

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"

    // 手动签到整体超时：超过仍未拿到明确结果则回调失败并释放界面，避免永久卡在「签到中…」
    private const val SIGN_TIMEOUT_MS = 90_000L
    // 状态检查（隐藏 WebView）整体超时
    private const val CHECK_TIMEOUT_MS = 15_000L
    // 离开风控页后，主动续签一次并多轮轮询结果，覆盖瑞数放行后页面异步渲染 / AJAX 提交
    private val POLL_DELAYS = longArrayOf(1200L, 2600L, 4000L)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var verifyDialog: Dialog? = null
    private var webViewRef: WebView? = null
    private var parentRef: ViewGroup? = null
    private var timeoutRunnable: Runnable? = null

    // ============================== 本地签到状态 ==============================

    fun updateLocalSignInTime() {
        SharedPreferencesHelper.getUserSp().edit {
            putLong("sp_key_last_sign_in_at", System.currentTimeMillis())
        }
    }

    fun isSignedToday(): Boolean {
        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        if (lastTime == 0L) return false
        val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastTime }
        val todayCal = java.util.Calendar.getInstance()
        return lastCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                lastCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    // ============================== 对外入口 ==============================

    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

    fun performSignIn(context: Context, onResult: (String, String) -> Unit) {
        mainHandler.post {
            val activity = context as? Activity
            if (activity != null && !activity.isFinishing) {
                startWebViewSignIn(activity, onResult)
            } else {
                // 后台（无 Activity）无法承载过盾 WebView，直接回调失败，交由调用方重试
                withResult(onResult, "FAILED", "当前无法启动验证界面，请进入「我的」手动签到")
            }
        }
    }

    // ============================== 签到主流程 ==============================

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewSignIn(activity: Activity, onResult: (String, String) -> Unit) {
        val finished = AtomicBoolean(false)
        var verifySeen = false
        var reloadedAfterVerify = false

        val parentView: ViewGroup =
            (activity.window?.decorView as? ViewGroup) ?: FrameLayout(activity)

        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            isScrollbarFadingEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = MOBILE_UA
        }
        webViewRef = webView
        parentRef = parentView

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // [PATCH] 停止清理 wzws_sid，保留 WAF 会话上下文
                            Timber.d("SignInManager: 跳过清理 wzws_sid")

        // 把持久化登录 / 凭证 Cookie 注入 WebView
        LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/").let { savedCookies ->
            if (savedCookies.isNotEmpty()) {
                val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
            }
        }
        CookieManager.getInstance().flush()

        fun finish(status: String, msg: String, finalUrl: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            cancelTimeout()
            if (status == "SUCCESS") updateLocalSignInTime()
            syncCookies(finalUrl ?: webView.url)
            releaseWebView(webView, parentView)
            dismissDialog()
            withResult(onResult, status, msg)
        }

        val pollTasks = mutableListOf<Runnable>()

        fun scheduleResultPoll(currentUrl: String?) {
            POLL_DELAYS.forEachIndexed { index, delay ->
                val task = Runnable {
                    if (finished.get()) return@Runnable
                    evaluatePageText(webView) { text ->
                        if (finished.get()) return@evaluatePageText
                        when {
                            isLoginExpired(text) -> finish("FAILED", "登录已失效，请重新登录", currentUrl)
                            isVerifyPage(currentUrl, text) -> Unit // 仍在验证页，等待下一轮 / 用户操作
                            isSignInSuccess(text) -> finish("SUCCESS", "签到成功！", currentUrl)
                            text.isBlank() -> Unit // 页面尚未渲染完成，继续下一轮
                            index == POLL_DELAYS.lastIndex ->
                                finish("FAILED", "未能确认签到状态，请稍后在网页端核实", currentUrl)
                            else -> Unit // 中间轮次未拿到明确结论，继续轮询
                        }
                    }
                }
                pollTasks.add(task)
                mainHandler.postDelayed(task, delay)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isVerifyUrl(url)) verifySeen = true
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                syncCookies(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncCookies(url)

                evaluatePageText(view) { text ->
                    if (finished.get()) return@evaluatePageText

                    if (isVerifyUrl(url) || isVerifyText(text)) {
                        verifySeen = true
                        return@evaluatePageText
                    }

                    // 刚离开风控页：放行凭证已写入 Cookie，主动重新申请一次任务，确保签到请求真正发出
                    if (verifySeen && !reloadedAfterVerify && url?.contains("mod=task") == true) {
                        reloadedAfterVerify = true
                        mainHandler.postDelayed({
                            if (!finished.get()) view?.loadUrl(APPLY_TASK_URL)
                        }, 800L)
                        return@evaluatePageText
                    }

                    scheduleResultPoll(url)
                }
            }
        }

        // 整体超时兜底：即便页面无跳转 / AJAX 不回包，也一定会回调并释放界面
        val timeout = Runnable {
            if (!finished.get()) {
                Timber.w("签到流程超时，回调失败并释放界面")
                finish("FAILED", "验证超时，请稍后重试")
            }
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, SIGN_TIMEOUT_MS)

        webView.loadUrl(APPLY_TASK_URL)
        showVerifyDialog(activity, webView) {
            finish("FAILED", "已取消签到")
        }
    }

    // ============================== 签到状态检查（隐藏 WebView） ==============================

    @SuppressLint("SetJavaScriptEnabled")
    fun checkSignInStatus(context: Context, onResult: (Boolean) -> Unit) {
        mainHandler.post {
            try {
                val activity = context as? Activity
                val parentView: ViewGroup =
                    (activity?.window?.decorView as? ViewGroup) ?: FrameLayout(LCGApp.context)

                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(100, 100)
                    alpha = 0.01f
                    settings.javaScriptEnabled = true
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            isScrollbarFadingEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = MOBILE_UA
                }
                parentView.addView(webView)

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/").let { savedCookies ->
                    if (savedCookies.isNotEmpty()) {
                        val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
                    }
                }
                CookieManager.getInstance().flush()

                val done = AtomicBoolean(false)
                var timeout: Runnable? = null
                fun release(result: Boolean) {
                // [PATCH] 如果确认已签到，且本地记录过期，立即更新本地时间戳
                if (result && !isSignedToday()) updateLocalSignInTime()
                    if (!done.compareAndSet(false, true)) return
                    timeout?.let { mainHandler.removeCallbacks(it) }
                    syncCookies(webView.url)
                    releaseWebView(webView, parentView)
                    onResult(result)
                }

                // 超时兜底：未在限定时间内拿到结论，按本地缓存状态返回
                timeout = Runnable { release(isSignedToday()) }
                mainHandler.postDelayed(timeout, CHECK_TIMEOUT_MS)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        syncCookies(url)
                        if (isVerifyUrl(url)) {
                            release(isSignedToday())
                            return
                        }
                        mainHandler.postDelayed({
                            evaluatePageText(view) { text ->
                                when {
                                    isVerifyText(text) -> release(isSignedToday())
                                    isSignInSuccess(text) -> {
                                        updateLocalSignInTime()
                                        release(true)
                                    }

                                    else -> release(false)
                                }
                            }
                        }, 2500L)
                    }
                }
                webView.loadUrl(APPLY_TASK_URL)
            } catch (e: Exception) {
                Timber.e(e)
                onResult(isSignedToday())
            }
        }
    }

    // ============================== 风控 / 结果识别 ==============================

    private fun isVerifyUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return listOf(
            "waf_text_verify", "waf_zw_verify", "wzws", "seccode",
            "captcha", "securityverify"
        ).any { url.contains(it, ignoreCase = true) }
    }

    private fun isVerifyText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return listOf(
            "请完成安全验证", "验证码访问", "安全验证", "完成验证",
            "输入验证码", "验证码错误", "滑动或点击", "wzws", "waf_zw_verify"
        ).any { text.contains(it, ignoreCase = true) }
    }

    private fun isVerifyPage(url: String?, text: String?): Boolean =
        isVerifyUrl(url) || isVerifyText(text)

    private fun isSignInSuccess(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val successMarks = listOf(
            "已申请", "已完成", "已签到", "已经签到", "签到成功",
            "领取成功", "任务完成", "恭喜"
        )
        return successMarks.any { text.contains(it) } ||
                (text.contains("获得") && text.contains("积分")) ||
                (text.contains("获得") && text.contains("奖励"))
    }

    private fun isLoginExpired(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return listOf("您需要先登录", "请先登录", "登录已失效").any { text.contains(it) }
    }

    private fun evaluatePageText(view: WebView?, cb: (String) -> Unit) {
        val js = """
            (function() {
                try {
                    var alert = document.querySelector(".alert_info p") ||
                                document.querySelector(".alert_info") ||
                                document.getElementById("messagetext");
                    if (alert && alert.innerText) return alert.innerText;
                    return document.body ? (document.body.innerText || "") : "";
                } catch(e) { return ""; }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js) { result ->
            cb(stripQuotes(result))
        } ?: cb("")
    }

    private fun stripQuotes(raw: String?): String {
        var text = raw?.trim().orEmpty()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
            text = text.substring(1, text.length - 1)
        }
        return text
    }

    // ============================== Cookie 与资源释放 ==============================

    private fun syncCookies(url: String?) {
        runCatching {
            if (!url.isNullOrBlank()) LCGCookieJar.syncFromWebView(url)
        }.onFailure { Timber.w(it, "同步 WebView Cookie 失败") }
    }

    private fun releaseWebView(webView: WebView, parent: ViewGroup?) {
        runCatching {
            webView.stopLoading()
            parent?.removeView(webView)
            webView.loadUrl("about:blank")
            webView.destroy()
        }.onFailure { Timber.w(it, "释放 WebView 失败") }
        if (webViewRef === webView) webViewRef = null
        if (parentRef === parent) parentRef = null
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun showVerifyDialog(activity: Activity, webView: WebView, onCancel: () -> Unit) {
        dismissDialog()
        verifyDialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen).apply {
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 100, 32, 32)
                setBackgroundColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(activity).apply {
                text = "需要安全验证，请滑动或点击完成"
                textSize = 16f
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnCancel = Button(activity).apply {
                text = "取消"
                setTextColor(Color.parseColor("#E53935"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { onCancel() }
            }
            header.addView(title)
            header.addView(btnCancel)
            root.addView(header)
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setContentView(root)
            // Dialog 被系统关闭 / 返回键 dismiss 时，同样走取消回调，避免界面与回调悬挂
            setOnDismissListener { onCancel() }
        }
        verifyDialog?.show()
    }

    private fun dismissDialog() {
        try {
            verifyDialog?.dismiss()
        } catch (e: Exception) {
            Timber.w(e, "关闭验证弹窗失败")
        }
        verifyDialog = null
    }

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        mainHandler.post { onResult(status, msg) }
    }

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
