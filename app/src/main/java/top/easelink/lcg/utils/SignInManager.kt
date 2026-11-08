package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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

    @SuppressLint("SetJavaScriptEnabled")
    fun performSignIn(onResult: (String, String) -> Unit) {
        if (!isSigningIn.compareAndSet(false, true)) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            val context: Context = LCGApp.context
            val webView = WebView(context)
            
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
                    
                    if (url?.contains("do=apply") == true) {
                        LCGCookieJar.syncFromWebView(url)
                        
                        // 让 JS 直接返回纯文本，不经过任何 JSON 序列化
                        view?.evaluateJavascript(
                            "(function() { return document.body.innerText; })();"
                        ) { result ->
                            // 最安全的处理方式：只去掉双引号，保留所有换行和特殊字符
                            val text = result?.replace("\"", "")?.trim().orEmpty()

                            if (text.contains("您需要先登录") || text.contains("请先登录")) {
                                withResult(onResult, "FAILED", "签到失败：登录已失效")
                            } else if (text.contains("已申请") || text.contains("已完成") || text.contains("已签到")) {
                                updateLocalSignInTime()
                                withResult(onResult, "ALREADY_DONE", "今日已签到")
                            } else if (text.contains("签到成功") || text.contains("获得") || text.contains("奖励")) {
                                updateLocalSignInTime()
                                withResult(onResult, "SUCCESS", "签到成功！")
                            } else if (text.isNotEmpty()) {
                                // 如果页面有其它反馈信息，直接展示（防止误判）
                                val hint = text.split("\n").find { it.contains("提示") || it.contains("失败") || it.contains("错误") } ?: "签到完成，请刷新查看"
                                withResult(onResult, "SUCCESS", hint)
                            } else {
                                withResult(onResult, "FAILED", "签到失败：未获取到反馈")
                            }
                            
                            destroyWebView(webView)
                        }
                    }
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
    
    private fun destroyWebView(webView: WebView) {
        Handler(Looper.getMainLooper()).post {
            try {
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
            } catch (e: Exception) {
                Timber.e(e, "WebView 销毁异常")
            }
        }
    }
}
