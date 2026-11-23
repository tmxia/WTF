package top.easelink.lcg.appinit

import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import top.easelink.framework.utils.APP_USER_AGENT
import top.easelink.lcg.utils.getCookiesFor
import top.easelink.lcg.utils.toHeaderString
import java.util.concurrent.TimeUnit

fun initCoil() {
    Coil.setImageLoader(LCGImageLoaderFactory)
}

object LCGImageLoaderFactory : ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader
        .Builder(LCGApp.context)
        .okHttpClient(buildClient())
        .build()

    private fun buildClient(): OkHttpClient {
        val headerInterceptor = Interceptor { chain ->
            val original = chain.request()
            val host = original.url.host.lowercase()
            val isLcgHost = host == "52pojie.cn" || host.endsWith(".52pojie.cn")

            if (isLcgHost) {
                val cookieHeader = getCookiesFor(original.url.toString()).toHeaderString()
                val newRequest = original.newBuilder()
                    .header("Referer", "https://www.52pojie.cn/")
                    .header("Sec-Fetch-Site", "same-site")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Dest", "image")
                    .header("X-Requested-With", "top.easelink.lcg")
                    .also { if (cookieHeader.isNotEmpty()) it.header("Cookie", cookieHeader) }
                    .header("Connection", "keep-alive")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("User-Agent", APP_USER_AGENT)
                    .build()

                // [ARCH-PHASE-9] 图片容灾：如果带防盗链头失败（如第三方图床），自动降级重试
                val response = chain.proceed(newRequest)
                if (!response.isSuccessful) {
                    val fallbackRequest = original.newBuilder()
                        .header("User-Agent", APP_USER_AGENT)
                        .build()
                    response.close() // 必须释放原连接
                    chain.proceed(fallbackRequest)
                } else {
                    response
                }
            } else {
                chain.proceed(original)
            }
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 3
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(headerInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
