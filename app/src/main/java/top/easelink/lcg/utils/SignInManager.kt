package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
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

    @SuppressLint("SetJavaScriptEnabled")
    fun performSignIn(onResult: (String, String) -> Unit) {
        if (!isSigningIn.compareAndSet(false, true)) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            val context: Context = LCGApp.context
            val webView = WebView(context)

            // 【核心修复 1】为了确保 JS 稳定执行，将 WebView 挂载到 Window（设为 1x1 像素透明）
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                setBackgroundColor(Color.TRANSPARENT)
                alpha = 0f
            }
            root.addView(webView, ViewGroup.LayoutParams(1, 1))
            
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams().apply {
                width = 1
                height = 1
                type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    android.view.WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            try {
                windowManager.addView(root, params)
            } catch (e: Exception) {
                Timber.tag("SignIn").e("添加 WebView 到 Window 失败（缺少悬浮窗权限），退回普通模式：${e.message}")
            }

            // 开始配置 WebView
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

                    // 【核心修复 2】不限制 do=apply，只要加载完成就等待 2 秒执行 JS
                    Handler(Looper.getMainLooper()).postDelayed({
                        LCGCookieJar.syncFromWebView(url ?: "")
                        
                        val js = """
                            (function() {
                                try {
                                    var alert = document.querySelector(".alert_info p");
                                    if (alert && alert.innerText) return alert.innerText;
                                    return document.body.innerText || document.documentElement.innerText || "";
                                } catch(e) { return "JS_ERROR:" + e.message; }
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(js) { result ->
                            // 【核心修复 3】把所有抓取到的内容完整输出到日志
                            Timber.tag("SignIn").d("抓取到内容: $result")
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
                                val hint = text.split("\n").find { it.contains("提示") || it.contains("失败") || it.contains("错误") || it.contains("申请") } ?: "签到完成，请刷新查看"
                                withResult(onResult, "SUCCESS", hint)
                            } else {
                                withResult(onResult, "FAILED", "未获取到反馈，请查看运行日志")
                            }
                            
                            // 销毁 WebView
                            try {
                                windowManager.removeView(root)
                            } catch (e: Exception) {}
                            webView.destroy()
                        }
                    }, 2000L) // 给 WAF 2 秒执行时间
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
