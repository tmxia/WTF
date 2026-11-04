package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

object SignInManager {

    



    @SuppressLint("SetJavaScriptEnabled")
    fun performSignIn(context: Context, onResult: (String, String) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context.applicationContext)
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("52pojie.cn") == true) {
                        val js = """
                            (function() {
                                if (document.title.indexOf("登录") > -1 || document.body.innerText.indexOf("您需要登录") > -1) {
                                    return "LOGIN_REQUIRED";
                                }
                                if (document.body.innerText.indexOf("已签到") > -1) {
                                    return "ALREADY_DONE";
                                }
                                var link = document.querySelector('a[href*="task&do=apply&id=2"]');
                                if (link) {
                                    link.click();
                                    return "SUCCESS";
                                }
                                return "FAILED";
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(js) { result ->
                            val cleanResult = result?.replace("\"", "")
                            when (cleanResult) {
                                "ALREADY_DONE" -> {
                                    onResult("ALREADY_DONE", "今日已签到")
                                    view.destroy()
                                }
                                "SUCCESS" -> {
                                    
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        onResult("SUCCESS", "签到成功！")
                                        view.destroy()
                                    }, 1500)
                                }
                                "LOGIN_REQUIRED" -> {
                                    onResult("LOGIN_REQUIRED", "登录已失效")
                                    view.destroy()
                                }
                                else -> {
                                    onResult("FAILED", "签到失败")
                                    view.destroy()
                                }
                            }
                        }
                    }
                }
            }
            webView.loadUrl("https://www.52pojie.cn/")
        }
    }
}
