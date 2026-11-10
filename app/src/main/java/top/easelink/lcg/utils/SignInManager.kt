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

    fun performSignIn(onResult: (String, String) -> Unit) {
        performSignIn(LCGApp.context, onResult)
    }

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
                    Timber.tag("SignIn").w("遭遇 WAF，启动可见 WebView 进行验证...")
                    Handler(Looper.getMainLooper()).post {
                        val activity = context as? Activity
                        if (activity != null && !activity.isFinishing) {
                            startWebViewSignIn(activity, onResult)
                        } else {
                            withResult(onResult, "FAILED", "无法启动验证界面")
                        }
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
    private fun startWebViewSignIn(activity: Activity, onResult: (String, String) -> Unit) {
        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false // 核心：放行所有重定向
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.contains("waf_text_verify") == true) {
                    isWafTriggered = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 如果当前 URL 还在验证页，或者包含 captcha，说明用户还在滑，不要急
                if (url?.contains("waf_text_verify") == true || url?.contains("captcha=") == true) {
                    return
                }

                // 如果验证通过跳转回来了，立刻同步 Cookie
                if (isWafTriggered) {
                    LCGCookieJar.syncFromWebView(url ?: "")
                }

                // 核心修复：延迟抓取，确保 DOM 渲染完成，防止抓到空页面
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
                        
                        Timber.tag("SignIn").d("WebView 抓取到内容: ${text.take(50)}")
                        
                        // 检查文本是否包含签到成功的字样
                        if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到") || text.contains("签到成功") || text.contains("获得")) {
                            updateLocalSignInTime()
                            dismissDialog()
                            withResult(onResult, "SUCCESS", "签到成功！")
                        } else if (text.isNotEmpty() && text.contains("您需要先登录")) {
                            dismissDialog()
                            withResult(onResult, "FAILED", "登录已失效")
                        }
                        // 如果什么都没抓到，可能还在加载，等待下一次 onPageFinished
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
