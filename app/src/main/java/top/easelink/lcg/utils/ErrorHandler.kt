package top.easelink.lcg.utils

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import top.easelink.lcg.network.NetworkException

/**
 * [ARCH-PHASE-7] 统一异常处理中心，将底层冷冰冰的报错翻译为用户友好的提示。
 */
object ErrorHandler {
    
    fun getFriendlyMessage(e: Throwable?): String {
        if (e == null) return "未知错误，请稍后重试"
        
        return when (e) {
            is NetworkException.WafBlocked -> "触发安全拦截，请稍后在网页端尝试，或重试"
            is UnknownHostException -> "网络连接已断开，请检查网络设置"
            is SocketTimeoutException -> "网络连接超时，请切换网络后重试"
            is IOException -> "网络不畅，请稍后重试"
            is top.easelink.lcg.ui.search.source.SearchAntiDefense.SearchFrozenException -> e.message ?: "搜索过快，已被限制"
            else -> {
                // 针对 Jsoup 常见的 403/412 状态码异常进行拦截
                val msg = e.message.orEmpty()
                if (msg.contains("403") || msg.contains("412")) {
                    "访问被拒绝，可能触发了风控验证"
                } else {
                    "加载失败，请稍后重试"
                }
            }
        }
    }
}
