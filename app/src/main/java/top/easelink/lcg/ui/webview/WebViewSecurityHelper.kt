package top.easelink.lcg.ui.webview

import android.webkit.WebSettings
import timber.log.Timber
import java.net.URL

/**
 * WebView 安全加固：域名白名单校验与文件访问关闭。
 *
 * 白名单仅放行 52pojie.cn 主域及子域；自定义 / 内部协议（lcg、about、javascript）放行，
 * 其余外部 http(s) 链接交由上层决定是否走系统浏览器。
 */
object WebViewSecurityHelper {

    private const val ALLOWED_ROOT_DOMAIN = "52pojie.cn"

    /** 判断 URL 是否属于可信域或内部协议。 */
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

    /** 关闭文件访问与跨域文件放行，降低本地文件泄露 / 跨域风险。 */
    fun applyHardening(settings: WebSettings) {
        settings.allowFileAccess = false
        settings.setAllowFileAccessFromFileURLs(false)
        settings.setAllowUniversalAccessFromFileURLs(false)
    }
}
