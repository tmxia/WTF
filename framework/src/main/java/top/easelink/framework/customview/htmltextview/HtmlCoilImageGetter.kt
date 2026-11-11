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
import okhttp3.Headers
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Html.ImageGetter 把 &lt;img&gt; 标签的下载交给 Coil。
 *
 * 关键点：原实现只保留一个 `currentJob`，每张图都会 `cancel()` 上一张 → 多图帖里**只有最后一张图能加载**。
 * 这里改成每张图独立 Job，存到 list；TextView detach 或 lifecycle 销毁时统一取消，
 * 不会再有"先加载的被后加载的杀掉"的情况。
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
                // 图片加载失败时静默：占位仍是 1×1 透明位图，不影响其他图。
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
        // 核心修复 1：如果是相对路径，自动补全为绝对路径
        var finalUrl = originalUrl
        if (finalUrl.startsWith("/")) {
            finalUrl = "https://www.52pojie.cn$finalUrl"
        } else if (!finalUrl.startsWith("http")) {
            finalUrl = "https://www.52pojie.cn/$finalUrl"
        }

        // 核心修复 2：注入 Referer 和 User-Agent，绕过论坛防盗链
        val headers = Headers.Builder()
            .add("Referer", "https://www.52pojie.cn/")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return Coil.imageLoader(context).execute(
            ImageRequest.Builder(context)
                .data(finalUrl)
                .headers(headers)
                .apply { lifecycleOwner?.let { lifecycle(it) } }
                .build()
        ).drawable!!
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

            // 核心修复：如果原图很小（通常是表情/小图标），不要去拉伸它！
            if (intrinsicWidth in 1..150) {
                // 按照屏幕密度适当缩放，但保持小巧，最大不超过 3 倍行高（约 54px）
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
