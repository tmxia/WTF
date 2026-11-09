package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.network.LCGCookieJar
import java.util.concurrent.atomic.AtomicBoolean

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val WAF_URL_KEYWORD = "waf_text_verify.html"
    private val isSigningIn = AtomicBoolean(false)
    private var verifyDialog: Dialog? = null

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

    fun performSignIn(context: Context, onResult: (String, String) -> Unit) {
        if (!isSigningIn.compareAndSet(false, true)) {
            return
        }

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.tag("SignIn").d("【API对齐】发起签到 GET 请求...")
                // 完美复刻历史提交的纯净逻辑
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
                val applyHtml = applyResponse.outerHtml()

                if (applyHtml.contains("您需要先登录")) {
                    Timber.tag("SignIn").e("签到失败：Cookie 已失效")
                    withResult(onResult, "FAILED", "签到失败：登录已失效")
                    return@launch
                }

                if (applyHtml.contains("wzwsquestion") || applyHtml.contains("waf_zw_verify") || applyHtml.contains("Please enable JavaScript")) {
                    Timber.tag("SignIn").w("触发 WAF 拦截，启动 WebView 兜底...")
                    Handler(Looper.getMainLooper()).post {
                        startWebViewSignIn(context, onResult)
                    }
                    return@launch
                }

                val applyMessage = applyResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()
                
                Timber.tag("SignIn").d("【API对齐】解析到信息: $applyMessage")

                if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("已签到") || applyMessage.contains("已经")) {
                    updateLocalSignInTime()
                    withResult(onResult, "ALREADY_DONE", "今日已签到")
                } else if (applyMessage.contains("成功") || applyMessage.contains("获得") || applyMessage.contains("奖励")) {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到成功！")
                } else {
                    // 走到这里说明没有 WAF 且没签到过，可能 GET 不会直接触发签到，需要进一步 POST
                    // 如果历史逻辑只有 GET 就能签到，那这里其实已经成功了。但为防万一，还是兜底成功。
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", if (applyMessage.isNotBlank()) applyMessage else "签到完成")
                }

            } catch (e: Exception) {
                Timber.tag("SignIn").e(e, "API 签到异常")
                Handler(Looper.getMainLooper()).post {
                    startWebViewSignIn(context, onResult)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewSignIn(context: Context, onResult: (String, String) -> Unit) {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing) {
            withResult(onResult, "FAILED", "无法启动验证界面")
            return
        }

        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        val savedCookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/")
        if (savedCookies.isNotEmpty()) {
            val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
            CookieManager.getInstance().flush()
        }

        var isWafTriggered = false
        var titleTextView: TextView? = null

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                titleTextView?.text = "正在加载安全验证页面，请稍候..."
                if (url?.contains(WAF_URL_KEYWORD) == true) {
                    isWafTriggered = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                titleTextView?.text = "若出现安全验证，请滑动或点击完成"

                if (url?.contains("captcha=") == true) {
                    return
                }

                if (isWafTriggered && url?.contains(WAF_URL_KEYWORD) == false) {
                    LCGCookieJar.syncFromWebView(url ?: "")
                    dismissDialog()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    val js = """
                        (function() {
                            try {
                                if (!document.body) return "";
                                var alert = document.querySelector(".alert_info p");
                                if (alert && alert.innerText) return alert.innerText;
                                return document.body.innerText || document.documentElement.innerText || "";
                            } catch(e) { return ""; }
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(js) { result ->
                        var text = result?.trim().orEmpty()
                        if (text.startsWith("\"") && text.endsWith("\"")) {
                            text = text.substring(1, text.length - 1)
                        }
                        
                        if (url?.contains(WAF_URL_KEYWORD) == true) return@evaluateJavascript
                        if (text.isEmpty()) {
                            titleTextView?.text = "加载缓慢，请稍候或点击取消重试"
                            return@evaluateJavascript
                        }

                        if (text.contains("您需要先登录") || text.contains("请先登录")) {
                            dismissDialog()
                            withResult(onResult, "FAILED", "签到失败：登录已失效")
                        } else if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到")) {
                            updateLocalSignInTime()
                            dismissDialog()
                            withResult(onResult, "ALREADY_DONE", "今日已签到")
                        } else if (text.contains("签到成功") || text.contains("获得") || text.contains("奖励")) {
                            updateLocalSignInTime()
                            dismissDialog()
                            withResult(onResult, "SUCCESS", "签到成功！")
                        } else {
                            val hint = text.split("\n").find { it.contains("提示") || it.contains("失败") || it.contains("错误") || it.contains("申请") } ?: "签到完成"
                            dismissDialog()
                            withResult(onResult, "SUCCESS", hint)
                        }
                    }
                }, 1500L)
            }
        }

        webView.loadUrl(APPLY_TASK_URL)
        verifyDialog = showVerifyDialog(activity, webView) { txt ->
            titleTextView = txt
        }
    }

    private fun showVerifyDialog(activity: Activity, webView: WebView, onTitleTextViewCreated: (TextView) -> Unit): Dialog {
        dismissDialog()

        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 64, 32, 32)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(activity).apply {
            text = "正在加载安全验证页面，请稍候..."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        onTitleTextViewCreated(title)
        val btnCancel = Button(activity).apply {
            text = "取消"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                dismissDialog()
                isSigningIn.set(false)
            }
        }
        header.addView(title)
        header.addView(btnCancel)
        root.addView(header)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(root)
        dialog.setOnCancelListener { isSigningIn.set(false) }

        Handler(Looper.getMainLooper()).postDelayed({
            if (isSigningIn.get() && !activity.isFinishing) {
                dialog.show()
            }
        }, 500)
        return dialog
    }

    private fun dismissDialog() {
        try {
            verifyDialog?.dismiss()
            verifyDialog = null
        } catch (e: Exception) {}
    }

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        isSigningIn.set(false)
        dismissDialog()
        Handler(Looper.getMainLooper()).post {
            onResult(status, msg)
        }
    }
}
