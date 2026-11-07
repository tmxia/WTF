package top.easelink.lcg.ui.main.discover.model

import androidx.annotation.DrawableRes

class ForumNavigationModel(
    val title: String,
    @field:DrawableRes val drawableRes: Int,
    val url: String,
    // 新增：用于标识是否为主版块以及其父版块的 ID
    val isSubForum: Boolean = false,
    val parentTitle: String? = null
)
