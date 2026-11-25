package top.easelink.lcg.utils

/**
 * 点赞链路的纯规则：URL -> pid 归属、站点返回文案 -> 成败判定。
 * 抽成纯函数便于 JVM 单测，避免"任何响应都当成功"的历史问题。
 */
object LikeRules {

    private val PID_REGEX = Regex("(?:^|[?&])pid=([0-9]+)")

    /** 从支持/取消支持链接里取楼层 pid，取不到返回 null。 */
    fun pidFromUrl(url: String?): String? {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return null
        return PID_REGEX.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private val ALREADY_LIKED_HINTS = listOf("已经赞", "已赞", "赞过", "已经支持", "已支持")

    private val FAILURE_HINTS = listOf(
        "失败", "不能", "无权", "无权限", "抱歉", "错误", "非法", "不存在",
        "请先登录", "需要先登录", "登录后", "余额", "间隔", "频繁"
    )

    private val SUCCESS_HINTS = listOf("成功", "感谢", "支持", "赞")

    /** 站点提示文案是否表示"已赞过"（幂等成功）。 */
    fun isAlreadyLiked(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return false
        return ALREADY_LIKED_HINTS.any { text.contains(it) }
    }

    /** 站点提示文案是否表示点赞成功。 */
    fun isSuccessMessage(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty() || text.contains("Error")) return false
        if (isAlreadyLiked(text)) return true
        if (FAILURE_HINTS.any { text.contains(it) }) return false
        return SUCCESS_HINTS.any { text.contains(it) }
    }

    /** 站点提示文案是否表示明确失败。 */
    fun isFailureMessage(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty() || text.contains("Error")) return true
        if (isAlreadyLiked(text)) return false
        return FAILURE_HINTS.any { text.contains(it) }
    }
}
