package top.easelink.lcg.network

import java.io.IOException

/**
 * 统一网络异常层级。子类型为 open，允许旧版 marker 异常（ui.main.model）继承映射，
 * 由 ErrorHandler 按类型统一翻译为用户友好提示。
 */
open class NetworkException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    /** 触发 WAF / 盾验证，需网页端过验证或重试。 */
    open class WafBlocked(message: String) : NetworkException(message)

    /** 被风控拦截或楼层内容被屏蔽，携带页面提示文案。 */
    open class Blocked(open val alertMessage: String) : NetworkException(alertMessage)

    /** 命中反爬 / 验证码页面，无法按正常帖子解析。 */
    open class AntiScraping(message: String = "anti-scraping") : NetworkException(message)

    /** 会话过期或未登录，需要重新走登录流程。 */
    open class LoginRequired(message: String = "login required") : NetworkException(message)

    /** 帖子不存在、已删除或无权访问。 */
    open class NotFound(message: String = "not found") : NetworkException(message)

    open class HttpError(val statusCode: Int, message: String) : NetworkException("HTTP $statusCode: $message")

    open class IoError(message: String, cause: Throwable?) : NetworkException(message, cause)

    open class TimeoutError(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    open class ParseError(message: String, cause: Throwable? = null) : NetworkException(message, cause)
}
