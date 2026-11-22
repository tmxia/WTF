package top.easelink.lcg.network

import java.io.IOException

/**
 * 统一网络异常包装。继承 [IOException] 以兼容上层既有的 `catch (IOException)` / `catch (Exception)` 兜底。
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    /** 命中 WAF 人机验证且未在窗口期内放行。 */
    class WafBlocked(message: String) : NetworkException(message)

    /** HTTP 错误状态码（403 / 429 / 514 等）。 */
    class HttpError(val statusCode: Int, message: String) : NetworkException("HTTP $statusCode: $message")

    /** 读取 / 写入 IO 异常。 */
    class IoError(message: String, cause: Throwable?) : NetworkException(message, cause)

    /** 请求超时。 */
    class TimeoutError(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    /** 页面解析失败。 */
    class ParseError(message: String, cause: Throwable? = null) : NetworkException(message, cause)
}
