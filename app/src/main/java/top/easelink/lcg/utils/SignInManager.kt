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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.LCGCookieJar

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

    


    @SuppressLint("SetJavaScriptEnabled")
    fun checkSignInStatus(context: Context, onResult: (Boolean) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = context as? Activity
                
                val parentView: ViewGroup = (activity?.window?.decorView as? ViewGroup) ?: FrameLayout(LCGApp.context)

                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(100, 100)
                    alpha = 0.01f
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                parentView.addView(webView)

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                
                val savedCookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/")
                if (savedCookies.isNotEmpty()) {
                    val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
                }
                CookieManager.getInstance().flush()

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        if (url?.contains("waf_text_verify") == true) {
                            onResult(isSignedToday())
                            parentView.removeView(webView)
                            webView.destroy()
                            return
                        }

                        Handler(Looper.getMainLooper()).postDelayed({
                            val js = """
                                (function() {
                                    try { return document.body.innerText || ""; } catch(e) { return ""; }
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(js) { result ->
                                var text = result?.trim().orEmpty()
                                if (text.startsWith("\"") && text.endsWith("\"")) {
                                    text = text.substring(1, text.length - 1)
                                }
                                
                                if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || text.contains("已经签到")) {
                                    updateLocalSignInTime() 
                                    onResult(true)
                                } else {
                                    onResult(false)
                                }
                                parentView.removeView(webView)
                                webView.destroy()
                            }
                        }, 3000L)
                    }
                }
                webView.loadUrl(APPLY_TASK_URL)
            } catch (e: Exception) {
                Timber.e(e)
                onResult(isSignedToday())
            }
        }
    }

    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

    fun performSignIn(context: Context, onResult: (String, String) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val activity = context as? Activity
            if (activity != null && !activity.isFinishing) {
                startWebViewSignIn(activity, onResult)
            } else {
                withResult(onResult, "FAILED", "无法启动验证界面")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebViewSignIn(activity: Activity, onResult: (String, String) -> Unit) {
        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        
        val currentCookie = CookieManager.getInstance().getCookie("https://www.52pojie.cn/")
        if (currentCookie != null) {
            currentCookie.split(";").forEach { cookie ->
                if (cookie.trim().startsWith("wzws_sid=")) {
                    CookieManager.getInstance().setCookie("https://www.52pojie.cn/", "wzws_sid=; Max-Age=0")
                }
            }
        }

        val savedCookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/")
        if (savedCookies.isNotEmpty()) {
            val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
        }
        CookieManager.getInstance().flush()

        var isWafTriggered = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.contains("waf_text_verify") == true) {
                    isWafTriggered = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("waf_text_verify") == true) return
                
                if (isWafTriggered) {
                    LCGCookieJar.syncFromWebView(url ?: "")
                    isWafTriggered = false
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    val js = """
                        (function() {
                            try {
                                var alert = document.querySelector(".alert_info p");
                                if (alert && alert.innerText) return alert.innerText;
                                return document.body.innerText || "";
                            } catch(e) { return ""; }
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(js) { result ->
                        var text = result?.trim().orEmpty()
                        if (text.startsWith("\"") && text.endsWith("\"")) {
                            text = text.substring(1, text.length - 1)
                        }
                        
                        if (text.contains("已申请") || text.contains("已完成") || text.contains("签到成功") || text.contains("获得") || text.contains("已经签到")) {
                            updateLocalSignInTime()
                            dismissDialog()
                            withResult(onResult, "SUCCESS", "签到成功！")
                        } else if (text.contains("您需要先登录")) {
                            dismissDialog()
                            withResult(onResult, "FAILED", "登录已失效")
                        } else if (text.isNotBlank() && !text.contains("请完成安全验证")) {
                            dismissDialog()
                            withResult(onResult, "FAILED", "未能确认签到状态，请稍后在网页端核实")
                        }
                    }
                }, 1500L)
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
                setOnClickListener { dismissDialog() }
            }
            header.addView(title)
            header.addView(btnCancel)
            root.addView(header)
            root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setContentView(root)
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
