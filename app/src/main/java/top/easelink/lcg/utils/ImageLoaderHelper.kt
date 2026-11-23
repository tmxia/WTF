package top.easelink.lcg.utils

import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import coil.load
import coil.transform.RoundedCornersTransformation
import top.easelink.framework.utils.dpToPx
import top.easelink.lcg.R







object ImageLoaderHelper {

    private const val DEFAULT_AVATAR = R.drawable.ic_noavatar_middle_gray

    fun loadAvatar(
        view: ImageView,
        url: String?,
        cornerDp: Float = 4f,
        lifecycleOwner: LifecycleOwner? = null,
        crossfade: Boolean = false,
        errorRes: Int = DEFAULT_AVATAR,
        placeholderRes: Int = DEFAULT_AVATAR,
    ) {
        view.load(url) {
            crossfade(crossfade)
            lifecycleOwner?.let { lifecycle(it) }
            if (cornerDp > 0f) {
                transformations(RoundedCornersTransformation(cornerDp.dpToPx(view.context)))
            }
            if (placeholderRes != 0) placeholder(placeholderRes)
            if (errorRes != 0) error(errorRes)
        }
    }
}
