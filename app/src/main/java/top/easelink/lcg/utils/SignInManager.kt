package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.edit
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.LCGCookieJar
import java.util.concurrent.atomic.AtomicBoolean

object SignInManager {

    private const val SIGN_IN_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private val isSigningIn = AtomicBoolean(false)

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
     * 供后台（Worker、LCGApp）使用的无参方法
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        doSignIn(LCGApp.context, onResult)
    }

    /**
     * 供前台（MeFragment）使用的带 Context 方法，用于挂载 Activity 唤醒 JS
     */
    fun performSignIn(context: Context, onResult: (String, String) -> Unit) {
        doSignIn(context, onResult)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun doSignIn(context: Context, onResult: (String, String) -> Unit) {
        if (!isSigningIn.compareAndSet(false, true)) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            val activity = context as? Activity
            var parentView: ViewGroup? = null

            // 核心修复 1：如果是 Activity，将其挂载到界面上以保证 100% 渲染
            if (activity != null && !activity.isFinishing) {
                parentView = activity.window.decorView as ViewGroup
            } else {
                // 核心修复 2：如果是后台 Worker，则用 FrameLayout 实例化兜底
                parentView = FrameLayout(LCGApp.context)
            }

            val webView = WebView(context)
            webView.layoutParams = ViewGroup.LayoutParams(1, 1)
            webView.alpha = 0.01f
            parentView.addView(webView)

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

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.tag("SignIn").d("页面加载完毕: $url")
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        LCGCookieJar.syncFromWebView(url ?: "")
                        
                        val js = """
                            (function() {
                                try {
                                    var alert = document.querySelector(".alert_info p");
                                    if (alert && alert.innerText) return alert.innerText;
                                    return document.body.innerText || document.documentElement.innerText || "";
                                } catch(e) { return ""; }
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(js) { result ->
                            val text = result?.replace("\"", "")?.trim().orEmpty()
                            Timber.tag("SignIn").d("抓取到内容: $text")

                            if (text.contains("您需要先登录") || text.contains("请先登录")) {
                                withResult(onResult, "FAILED", "签到失败：登录已失效")
                            } else if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到")) {
                                updateLocalSignInTime()
                                withResult(onResult, "ALREADY_DONE", "今日已签到")
                            } else if (text.contains("签到成功") || text.contains("获得") || text.contains("奖励")) {
                                updateLocalSignInTime()
                                withResult(onResult, "SUCCESS", "签到成功！")
                            } else if (text.isNotEmpty()) {
                                val hint = text.split("\n").find { it.contains("提示") || it.contains("失败") || it.contains("错误") || it.contains("申请") } ?: "签到完成，请刷新查看"
                                withResult(onResult, "SUCCESS", hint)
                            } else {
                                withResult(onResult, "FAILED", "签到失败：页面无响应")
                            }
                            
                            try {
                                parentView?.removeView(webView)
                                webView.destroy()
                            } catch (e: Exception) {}
                        }
                    }, 2000L)
                }
            }
            
            webView.loadUrl(SIGN_IN_URL)
        }
    }
    
    private fun withResult(onResult: (String, String) -> Unit, status: String, msg: String) {
        isSigningIn.set(false)
        Handler(Looper.getMainLooper()).post {
            onResult(status, msg)
        }
    }
}
