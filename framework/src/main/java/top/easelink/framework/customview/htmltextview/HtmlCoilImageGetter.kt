package top.easelink.framework.customview.htmltextview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Html.ImageGetter 把 <img> 标签的下载交给 Coil。
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
            } catch (_: Exception) {
                // 图片加载失败时静默：占位仍是 1×1 透明位图
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
        return holder
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
        // 1. 路径智能补全
        var finalUrl = originalUrl.trim()
        if (finalUrl.startsWith("//")) {
            finalUrl = "https:$finalUrl"
        } else if (finalUrl.startsWith("/")) {
            finalUrl = "https://www.52pojie.cn$finalUrl"
        } else if (!finalUrl.startsWith("http")) {
            finalUrl = "https://www.52pojie.cn/$finalUrl"
        }

        // 2. 根据域名动态分配 Referer
        val referer = if (finalUrl.contains("static.52pojie.cn")) {
            "https://static.52pojie.cn/"
        } else {
            "https://www.52pojie.cn/"
        }

        // 3. 构建完整的浏览器级别请求头（彻底绕过 WAF 检测）
        val headers = Headers.Builder()
            .add("Referer", referer)
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("Sec-Fetch-Site", "same-site")
            .add("Sec-Fetch-Mode", "no-cors")
            .add("Sec-Fetch-Dest", "image")
            .add("Upgrade-Insecure-Requests", "1")
            .build()

        // 4. 执行请求
        return Coil.imageLoader(context).execute(
            ImageRequest.Builder(context)
                .data(finalUrl)
                .headers(headers)
                .apply { lifecycleOwner?.let { lifecycle(it) } }
                .build()
        ).drawable ?: BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8))
    }

    private inner class BitmapDrawablePlaceholder :
        BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)) {

        private var drawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            drawable?.draw(canvas)
        }

        fun setDrawable(drawable: Drawable) {
            this.drawable = drawable
            val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 0
            val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1

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

                drawable.setBounds(0, 0, targetWidth, scaledHeight)
                setBounds(0, 0, targetWidth, scaledHeight)
            }

            // 触发 TextView 重排
            textView.text = textView.text
        }
    }
}
