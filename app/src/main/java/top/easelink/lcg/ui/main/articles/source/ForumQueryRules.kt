package top.easelink.lcg.ui.main.articles.source

/**
 * 板块列表排序 / 时间筛选的纯规则集合（不依赖 Android 与 ViewModel，便于离线单测）。
 */
object ForumQueryRules {

    /** 一天对应的秒数（Discuz dateline 过滤按秒计）。 */
    const val SECONDS_PER_DAY = 86400

    /** 允许的排序字段白名单。 */
    val allowedOrderby: Set<String> = setOf("dateline", "lastpost")

    /** 允许的时间范围（天）白名单。 */
    val allowedDateline: Set<Int> = setOf(1, 2, 7, 30, 90, 365)

    /** 校验排序字段，非法返回 null。 */
    fun safeOrderby(orderby: String?): String? = orderby?.takeIf { it in allowedOrderby }

    /** 校验时间天数，非法返回 null。 */
    fun safeDateline(days: Int?): Int? = days?.takeIf { it in allowedDateline }

    /** 构造排序参数；未选排序字段时返回空串。 */
    fun orderbyParam(safe: String?): String = if (safe != null) "&orderby=$safe" else ""

    /** 构造时间过滤参数；未选时间范围时返回空串。 */
    fun datelineFilter(days: Int?): String =
        days?.let { "&filter=dateline&dateline=${it * SECONDS_PER_DAY}" } ?: ""
}
