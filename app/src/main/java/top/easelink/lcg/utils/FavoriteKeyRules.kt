package top.easelink.lcg.utils

/** 收藏条目的稳定标识：帖子 tid。列表页 href 与详情页 URL 形式不同，按 tid 对齐。 */
object FavoriteKeyRules {
    private val THREAD = Regex("thread-(\\d+)")
    private val TID = Regex("(?:^|[?&])tid=(\\d+)")

    fun threadId(url: String?): String? {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return null
        THREAD.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        return TID.find(text)?.groupValues?.getOrNull(1)
    }
}
