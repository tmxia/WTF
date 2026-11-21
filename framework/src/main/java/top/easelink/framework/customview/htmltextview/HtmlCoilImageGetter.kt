package top.easelink.framework.customview.htmltextview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Html.ImageGetter 把 <img> 标签的下载交给 Coil。
 *
 * 修复要点：
 * 1. 自有域（*.52pojie.cn）携带论坛 Referer；第三方图床不发外站 Referer，避免被防盗链 403。
 * 2. URL 规范化：协议相对路径 / 根相对路径 / 无协议相对路径补全；仅自有域 http 升级 https。
 * 3. 加载失败打印日志，不再静默吞掉；占位 Drawable 尺寸做除零/NaN 防御。
 * 4. 暴露 [cancelJobs]，供 RecyclerView 复用时取消尚未完成的旧任务，避免串图。
 */
class HtmlCoilImageGetter(
    private val context: Context,
    private val textView: TextView,
    private val lifecycleOwner: LifecycleOwner? = null
) : Html.ImageGetter {
    private val jobs = CopyOnWriteArrayList<Job>()

    private val scope: CoroutineScope = lifecycleOwner?.lifecycleScope
        ?: createDetachAwareScope()

    override fun getDrawable(url: String): Drawable {
        val holder = BitmapDrawablePlaceholder()
        val job = scope.launch {
            try {
                val drawable = loadImage(url)
                withContext(Dispatchers.Main) {
                    holder.setDrawable(drawable)
                }
            } catch (e: Exception) {
                Timber.e(e, "[HtmlImage] 图片加载失败: %s", url)
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
        return holder
    }

    /** 取消尚未完成的图片加载任务（ViewHolder 复用 / 重新绑定时调用）。 */
    fun cancelJobs() {
        jobs.forEach { runCatching { it.cancel() } }
        jobs.clear()
    }

    private fun createDetachAwareScope(): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        textView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                scope.cancel()
                textView.removeOnAttachStateChangeListener(this)
            }
        })
        return scope
    }

    private suspend fun loadImage(originalUrl: String): Drawable {
        val finalUrl = normalizeUrl(originalUrl)
            ?: return BitmapDrawable(
                context.resources,
                Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
            )

        val isLcg = isLcgHost(finalUrl)

        val headers = Headers.Builder().apply {
            add(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")
            if (isLcg) {
                // 自有域：携带论坛 Referer，绕过论坛 CDN 防盗链
                add("Referer", REFERRER)
                add("Sec-Fetch-Site", "same-site")
            } else {
                // 第三方图床：不带外站 Referer，Sec-Fetch-Site 标 cross-site，避免被防盗链拒绝
                add("Sec-Fetch-Site", "cross-site")
            }
            add("Sec-Fetch-Mode", "no-cors")
            add("Sec-Fetch-Dest", "image")
        }.build()

        return Coil.imageLoader(context).execute(
            ImageRequest.Builder(context)
                .data(finalUrl)
                .headers(headers)
                .apply { lifecycleOwner?.let { lifecycle(it) } }
                .build()
        ).drawable ?: BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
        )
    }

    /** 规范化图片 URL；无法识别（空 / data: 等）时返回 null。 */
    private fun normalizeUrl(raw: String): String? {
        var url = raw.trim().removeSurrounding("\"", "'").trim()
        if (url.isEmpty() || url.startsWith("data:")) return null

        // 解码可能残留的 HTML 实体
        url = url.replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        url = url.trim()
        if (url.isEmpty()) return null

        when {
            url.startsWith("//") -> url = "https:$url"
            url.startsWith("/") -> url = "$SERVER_BASE$url"
            !url.startsWith("http://") && !url.startsWith("https://") ->
                url = "$SERVER_BASE/$url"
        }

        // 仅本站自有域把 http 升级为 https；第三方老图床保留原协议，避免 TLS 失败
        if (url.startsWith("http://") && isLcgHost(url)) {
            url = url.replaceFirst("http://", "https://")
        }
        return url
    }

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")

    private fun isLcgHost(url: String): Boolean {
        val host = hostOf(url).lowercase()
        return host == "52pojie.cn" || host.endsWith(".52pojie.cn")
    }

    private inner class BitmapDrawablePlaceholder :
        BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)) {
        private var drawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            drawable?.draw(canvas)
        }

        fun setDrawable(drawable: Drawable) {
            this.drawable = drawable

            // 尺寸防御：取不到 intrinsic 宽/高时给默认比例，避免除零 / NaN 导致图片不可见
            var intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 0
            var intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 0
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                intrinsicWidth = (textView.measuredWidth -
                    textView.paddingLeft - textView.paddingRight)
                    .coerceAtLeast(context.resources.displayMetrics.widthPixels)
                intrinsicHeight = (intrinsicWidth * 0.6f).toInt().coerceAtLeast(1)
            }

            // 如果原图很小（通常是表情/小图标），不要去拉伸它
            if (intrinsicWidth in 1..150) {
                val density = context.resources.displayMetrics.density
                val finalWidth = (intrinsicWidth * density).toInt().coerceAtMost(72)
                val finalHeight = (intrinsicHeight * density).toInt().coerceAtMost(72)

                drawable.setBounds(0, 0, finalWidth, finalHeight)
                setBounds(0, 0, finalWidth, finalHeight)
            } else {
                // 正常大图，按原来的逻辑缩放到屏幕宽度
                val targetWidth = if (textView.measuredWidth > 0) {
                    textView.measuredWidth - textView.paddingLeft - textView.paddingRight
                } else {
                    context.resources.displayMetrics.widthPixels
                }.coerceAtLeast(1)

                val scaledHeight = (targetWidth * intrinsicHeight / intrinsicWidth.toFloat()).toInt()
                    .coerceAtLeast(1)

                drawable.setBounds(0, 0, targetWidth, scaledHeight)
                setBounds(0, 0, targetWidth, scaledHeight)
            }

            // 触发 TextView 重排
            textView.text = textView.text
        }
    }

    companion object {
        private const val SERVER_BASE = "https://www.52pojie.cn"
        private const val REFERRER = "https://www.52pojie.cn/"
    }
}
