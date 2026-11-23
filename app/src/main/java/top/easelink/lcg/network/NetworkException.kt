package top.easelink.lcg.network

import java.io.IOException




sealed class NetworkException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    
    class WafBlocked(message: String) : NetworkException(message)

    
    class HttpError(val statusCode: Int, message: String) : NetworkException("HTTP $statusCode: $message")

    
    class IoError(message: String, cause: Throwable?) : NetworkException(message, cause)

    
    class TimeoutError(message: String, cause: Throwable? = null) : NetworkException(message, cause)

    
    class ParseError(message: String, cause: Throwable? = null) : NetworkException(message, cause)
}
