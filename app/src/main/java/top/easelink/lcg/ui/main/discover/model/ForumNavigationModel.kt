package top.easelink.lcg.ui.main.discover.model

import androidx.annotation.DrawableRes

data class ForumNavigationModel(
    val title: String,
    @field:DrawableRes val drawableRes: Int,
    val url: String,
    val isSubForum: Boolean = false,
    val parentTitle: String? = null
, val fid: String? = null)
