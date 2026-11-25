package top.easelink.lcg.ui.main.model

/** 点赞失败：携带点赞链接，供列表回滚乐观置亮的图标。 */
data class LikeFailedEvent(val url: String)
