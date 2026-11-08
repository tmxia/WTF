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
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.network.LCGCookieJar
import java.util.concurrent.atomic.AtomicBoolean

object SignInManager {

    private const val SIGN_IN_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
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

    fun performSignIn(onResult: (String, String) -> Unit) {
        doSignIn(LCGApp.context, onResult)
    }

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
            // 如果不是在 Activity 环境，或者 Activity 已销毁，无法弹窗，直接中断
            if (activity == null || activity.isFinishing) {
                isSigningIn.set(false)
                onResult("FAILED", "触发安全验证，请打开App手动前往签到")
                return@post
            }

            val webView = WebView(activity)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            
            // 同步已有的 Cookie
            val savedCookies = LCGCookieJar.cookiesForUrl("https://www.52pojie.cn/")
            if (savedCookies.isNotEmpty()) {
                val cookieStr = savedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                CookieManager.getInstance().setCookie("https://www.52pojie.cn/", cookieStr)
                CookieManager.getInstance().flush()
            }

            var isWafTriggered = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url?.contains(WAF_URL_KEYWORD) == true) {
                        isWafTriggered = true
                        // 已经拦截，此时弹窗应该已经显示，什么都不用做，等待用户交互
                        Timber.tag("SignIn").d("检测到 WAF 拦截，等待用户手动完成验证...")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.tag("SignIn").d("页面加载完毕: $url")

                    // 如果用户刚完成验证，URL 会跳出 WAF 页面
                    if (isWafTriggered && url?.contains(WAF_URL_KEYWORD) == false) {
                        Timber.tag("SignIn").d("WAF 验证通过，正在同步 Cookie...")
                        LCGCookieJar.syncFromWebView(url ?: "")
                        // 验证通过后，关闭弹窗
                        dismissDialog()
                    }

                    // 继续延时等待 DOM 渲染并抓取结果
                    Handler(Looper.getMainLooper()).postDelayed({
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

                            // 如果仍然是 WAF 页面，不处理，让用户继续验证
                            if (url?.contains(WAF_URL_KEYWORD) == true) {
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
                            } else if (text.isNotEmpty()) {
                                val hint = text.split("\n").find { it.contains("提示") || it.contains("失败") || it.contains("错误") || it.contains("申请") } ?: "签到完成，请刷新查看"
                                dismissDialog()
                                withResult(onResult, "SUCCESS", hint)
                            } else {
                                // 如果没抓到内容，且没触发 WAF，可能是页面还没加载完，或静默失败
                                dismissDialog()
                                withResult(onResult, "FAILED", "签到失败：页面无响应")
                            }
                        }
                    }, 2000L)
                }
            }

            webView.loadUrl(SIGN_IN_URL)

            // 为了兼容可能需要手动验证的情况，只要触发了 WAF，就立刻弹出全屏 WebView
            // 由于我们在 onPageStarted 才知道是不是 WAF，这里采用最简单的策略：直接弹出弹窗，让用户看着它加载
            // 如果秒过，用户只看个白屏，然后弹窗自动关。如果卡住，用户可以手动滑验证。
            showVerifyDialog(activity, webView)
        }
    }

    private fun showVerifyDialog(activity: Activity, webView: WebView) {
        dismissDialog()

        verifyDialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen).apply {
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }
            
            // 顶部提示栏
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 64, 32, 32)
                setBackgroundColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(activity).apply {
                text = "若出现安全验证，请滑动或点击完成，通过后将自动签到"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
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
            setContentView(root)
            
            setOnCancelListener {
                isSigningIn.set(false)
            }
            
            // 延迟 500ms 弹出，给用户一点反应时间，避免白屏闪烁
            Handler(Looper.getMainLooper()).postDelayed({
                if (isSigningIn.get() && !activity.isFinishing) {
                    show()
                }
            }, 500)
        }
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
