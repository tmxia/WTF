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
            // 自有域（www/static/avatar/attach/down 等）携带登录 Cookie 与论坛 Referer；
            // 第三方图床不注入 Cookie / Referer，避免外站防盗链拦截。
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
                    .header(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                    )
                    .header("Accept-Encoding", "gzip, deflate")
                    .header(
                        "User-Agent",
                        APP_USER_AGENT
                    )
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(original)
            }
        }

        // 限制图片并发：全局最多 8 路、同一宿主最多 3 路，避免长帖正文/楼层一次性
        // 拉出几十张同域图片并发请求，短时间 QPS 过高触发站点风控。
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
