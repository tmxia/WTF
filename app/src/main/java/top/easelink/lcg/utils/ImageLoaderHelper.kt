package top.easelink.lcg.utils

import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import coil.load
import coil.transform.RoundedCornersTransformation
import top.easelink.framework.utils.dpToPx
import top.easelink.lcg.R

/**
 * 图片（头像）加载统一入口：占位 / 错误态、圆角、生命周期绑定收口于此。
 *
 * 请求头、防盗链 Referer / Cookie、超时由全局 Coil ImageLoader（LCGImageLoaderFactory）统一注入，
 * 本封装只负责视图层的展示模板；既有页面可在重构时逐步替换为该入口。
 */
object ImageLoaderHelper {

    fun loadAvatar(
        view: ImageView,
        url: String?,
        cornerDp: Float = 4f,
        lifecycleOwner: LifecycleOwner? = null,
    ) {
        view.load(url) {
            crossfade(false)
            lifecycleOwner?.let { lifecycle(it) }
            if (cornerDp > 0f) {
                transformations(RoundedCornersTransformation(cornerDp.dpToPx(view.context)))
            }
            placeholder(R.drawable.ic_noavatar_middle_gray)
            error(R.drawable.ic_noavatar_middle_gray)
        }
    }
}
