package top.easelink.lcg.utils

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import top.easelink.framework.utils.APP_USER_AGENT

/**
 * [ARCH-PHASE-5] 统一 WebView 的底层配置与指纹环境，防止瑞数盾因环境参数不一致而拦截。
 */
object WebViewConfigurator {
    @SuppressLint("SetJavaScriptEnabled")
    fun setupStandardWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowContentAccess = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 统一使用全局 APP_USER_AGENT，确保与网络层请求头一致
            userAgentString = APP_USER_AGENT
            // 强制开启缓存，保证 JS 环境一致
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        // 统一开启软件渲染，避免部分机型 Canvas 指纹获取不到
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.isScrollbarFadingEnabled = true
        
        // 强制接收所有 Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
}
