package top.easelink.lcg.ui.webview

import android.webkit.WebSettings
import timber.log.Timber
import java.net.URL







object WebViewSecurityHelper {

    private const val ALLOWED_ROOT_DOMAIN = "52pojie.cn"

    
    fun isAllowedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("lcg:") || lower.startsWith("about:") || lower.startsWith("javascript:")) {
            return true
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false
        }
        val host = runCatching { URL(lower).host }.getOrNull() ?: run {
            Timber.w("无法解析 WebView 跳转域名: $url")
            return false
        }
        return host == ALLOWED_ROOT_DOMAIN || host.endsWith(".$ALLOWED_ROOT_DOMAIN")
    }

    
    fun applyHardening(settings: WebSettings) {
        settings.allowFileAccess = false
        settings.setAllowFileAccessFromFileURLs(false)
        settings.setAllowUniversalAccessFromFileURLs(false)
    }
}
