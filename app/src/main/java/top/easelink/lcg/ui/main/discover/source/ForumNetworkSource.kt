package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    // 垃圾版块黑名单关键字
    private val BLACKLIST = listOf("大赛", "竞赛", "公告", "活动", "招募", "征集", "专帖", "申请", "建议")

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            Timber.d("【板块API】开始暴力遍历 forum.php ...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            
            // 暴力抓取全页面所有的 <a> 标签，不管它在哪
            val allLinks = doc.select("a[href]")
            Timber.d("【板块API】全页找到 ${allLinks.size} 个链接，开始提取...")

            for (link in allLinks) {
                val href = link.attr("href").trim()
                val title = link.text().trim()

                if (title.isNotBlank() && isValidForumUrl(href, title)) {
                    // 标准化 URL (有些是相对路径，有些带参数，这里简单处理下用于去重)
                    val standardUrl = if (href.startsWith("http")) href else href
                    
                    if (uniqueUrls.add(standardUrl)) {
                        list.add(
                            ForumNavigationModel(
                                title = cleanTitle(title),
                                drawableRes = getIconForForum(title),
                                url = standardUrl,
                                isSubForum = isSubForumLink(link)
                            )
                        )
                    }
                }
            }

            Timber.d("【板块API】解析完成，最终确认 ${list.size} 个真实板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求异常")
        }
        return list
    }

    // 严格校验是否为真实版块
    private fun isValidForumUrl(href: String, title: String): Boolean {
        // 1. 必须包含论坛版块特征
        val isForumPattern = (href.contains("forum-") && href.endsWith("-1.html")) || 
                             href.contains("forumdisplay&fid=") ||
                             href.contains("forum.php?mod=forumdisplay")
        if (!isForumPattern) return false

        // 2. 排除帖子查看、发帖、搜索等链接
        if (href.contains("viewthread") || href.contains("action=") || href.contains("mod=post")) return false

        // 3. 黑名单过滤
        if (BLACKLIST.any { title.contains(it) }) return false
        
        // 4. 纯数字或极短的无意义标题过滤
        if (title.length < 2 || title.toIntOrNull() != null) return false

        return true
    }

    // 利用 DOM 结构判断是否为子版块（如果它的父节点含有 dd 标签）
    private fun isSubForumLink(element: org.jsoup.nodes.Element): Boolean {
        return element.parents().any { it.tagName() == "dd" }
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace("『", "").replace("』", "")
            .replace("《", "").replace("》", "")
            .replace("【", "").replace("】", "")
            .replace("\n", "").replace(" ", "")
            .trim()
    }

    private fun getIconForForum(title: String): Int {
        return when {
            title.contains("安全") || title.contains("逆向") || title.contains("病毒") || title.contains("破解") -> R.drawable.ic_sercurite
            title.contains("软件") || title.contains("程序") || title.contains("工具") || title.contains("资源") -> R.drawable.ic_software
            title.contains("原创") || title.contains("发布") -> R.drawable.ic_creator
            title.contains("水") || title.contains("闲聊") -> R.drawable.ic_water
            title.contains("福利") || title.contains("活动") -> R.drawable.ic_welfare
            else -> R.drawable.ic_software
        }
    }
}
