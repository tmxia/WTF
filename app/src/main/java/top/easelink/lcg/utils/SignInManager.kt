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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.network.LCGCookieJar
import java.io.IOException

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
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

    /**
     * 【验证机制】静默 GET 探测状态，遇到 WAF 直接保留本地状态
     */
    fun checkSignInStatus(onResult: (Boolean) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val doc = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val text = doc.text()
                if (doc.outerHtml().contains("wzwsquestion") || doc.outerHtml().contains("waf_zw_verify")) {
                    withContext(Dispatchers.Main) { onResult(isSignedToday()) }
                    return@launch
                }
                if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || text.contains("已经签到")) {
                    updateLocalSignInTime()
                    withContext(Dispatchers.Main) { onResult(true) }
                } else {
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(isSignedToday()) }
            }
        }
    }

    /**
     * 【手工签到机制】无参重载（供 LCGApp 和 SignInWorker 调用）
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

    /**
     * 【手工签到机制】带 Context 重载（带隐形 WebView 过盾）
     */
    fun performSignIn(context: android.content.Context, onResult: (String, String) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.tag("SignIn").d("【手工签到】发起请求...")
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                val applyMessage = applyResponse.getElementsByClass("alert_info").first()?.selectFirst("p")?.text().orEmpty()
                
                if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("签到成功") || applyMessage.contains("获得")) {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到成功！")
                } else {
                    updateLocalSignInTime()
                    withResult(onResult, "SUCCESS", "签到完成")
                }
            } catch (e: IOException) {
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    Timber.tag("SignIn").w("遭遇 WAF，启动隐形 WebView 静默过盾...")
                    Handler(Looper.getMainLooper()).post {
                        startHeadlessWebView(context, onResult)
                    }
                } else {
                    withResult(onResult, "FAILED", "网络异常: ${e.message}")
                }
            } catch (e: Exception) {
                withResult(onResult, "FAILED", "未知异常: ${e.message}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startHeadlessWebView(context: Context, onResult: (String, String) -> Unit) {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing) {
            withResult(onResult, "FAILED", "无法启动验证环境")
            return
        }

        val webView = WebView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0.01f
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        val decorView = activity.window.decorView as ViewGroup
        decorView.addView(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        val savedCookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/")
        if (savedCookies.isNotEmpty()) {
            val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
            CookieManager.getInstance().flush()
        }

        var hasResolved = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url?.contains("waf_text_verify") == true) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!hasResolved) {
                            hasResolved = true
                            decorView.removeView(webView)
                            webView.destroy()
                            startVisibleWebViewSignIn(activity, onResult)
                        }
                    }, 5000L)
                    return
                }

                if (!hasResolved) {
                    hasResolved = true
                    LCGCookieJar.syncFromWebView(url ?: "")
                    decorView.removeView(webView)
                    webView.destroy()

                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
                            val applyMessage = applyResponse.getElementsByClass("alert_info").first()?.selectFirst("p")?.text().orEmpty()
                            updateLocalSignInTime()
                            withResult(onResult, "SUCCESS", if (applyMessage.isNotBlank()) applyMessage else "签到成功！")
                        } catch (e: Exception) {
                            updateLocalSignInTime()
                            withResult(onResult, "SUCCESS", "签到请求已送达")
                        }
                    }
                }
            }
        }

        webView.loadUrl(APPLY_TASK_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startVisibleWebViewSignIn(activity: Activity, onResult: (String, String) -> Unit) {
        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        var isWafTriggered = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (url?.contains("waf_text_verify") == true) isWafTriggered = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url?.contains("captcha=") == true) return

                if (isWafTriggered && url?.contains("waf_text_verify") == false) {
                    LCGCookieJar.syncFromWebView(url ?: "")
                    dismissDialog()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    val js = "(function() { try { return document.body.innerText || document.documentElement.innerText || ''; } catch(e) { return ''; } })();"
                    view?.evaluateJavascript(js) { result ->
                        var text = result?.trim().orEmpty()
                        if (text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
                        if (url?.contains("waf_text_verify") == true) return@evaluateJavascript

                        if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || text.contains("签到成功")) {
                            updateLocalSignInTime()
                            dismissDialog()
                            withResult(onResult, "SUCCESS", "签到成功！")
                        }
                    }
                }, 2000L)
            }
        }

        webView.loadUrl(APPLY_TASK_URL)
        showVerifyDialog(activity, webView)
    }

    private fun showVerifyDialog(activity: Activity, webView: WebView) {
        dismissDialog()
        verifyDialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen).apply {
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
                text = "需要安全验证，请滑动完成"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnCancel = Button(activity).apply {
                text = "取消"
                setTextColor(Color.RED)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { dismissDialog() }
            }
            header.addView(title)
            header.addView(btnCancel)
            root.addView(header)
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setContentView(root)
            setOnCancelListener { }
        }
        verifyDialog?.show()
    }

    private fun dismissDialog() {
        try {
            verifyDialog?.dismiss()
            verifyDialog = null
        } catch (e: Exception) {}
    }

    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        GlobalScope.launch(Dispatchers.Main) {
            onResult(status, msg)
        }
    }
}
