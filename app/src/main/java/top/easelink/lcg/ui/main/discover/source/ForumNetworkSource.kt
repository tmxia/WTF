package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    // 极端黑名单过滤：剔除所有包含操作、管理、公告等无关字眼的标题
    private val BLACKLIST = listOf(
        "新帖", "举报", "投诉", "管理", "公告", "活动", "大赛", "竞赛", 
        "招募", "欢迎", "处理", "站务", "福利", "建议", "申请", "专帖", "置顶"
    )

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            Timber.d("【板块API】开始严苛模式遍历 forum.php ...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            
            // 仅提取可能是版块的 <a> 标签
            val allLinks = doc.select("a[href*=forum-], a[href*=forumdisplay]")
            Timber.d("【板块API】找到 ${allLinks.size} 个候选链接")

            for (link in allLinks) {
                val href = link.attr("href").trim()
                val title = link.text().trim()

                // 1. 严格且合规的 URL 判断
                if (!isValidForumUrl(href)) continue
                
                // 2. 极端严苛的标题过滤
                if (!isValidForumTitle(title)) continue

                val standardUrl = if (href.startsWith("http")) href else href
                if (uniqueUrls.add(standardUrl)) {
                    // 在构造时直接判定是否为子版块，彻底解决上一版 copy() 导致的报错
                    val isSub = link.parents().any { it.tagName() == "dd" }
                    
                    list.add(
                        ForumNavigationModel(
                            title = cleanTitle(title),
                            drawableRes = getIconForForum(title),
                            url = standardUrl,
                            isSubForum = isSub
                        )
                    )
                }
            }

            Timber.d("【板块API】净化完成，最终确认 ${list.size} 个干净板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求异常")
        }
        return list
    }

    // 严格 URL 校验：彻底击毙伪装的快捷入口
    private fun isValidForumUrl(href: String): Boolean {
        val isPureForum = href.matches(Regex("forum-\\d+-1\\.html")) || href.contains("forumdisplay&fid=")
        val isJunk = href.contains("newthread") || href.contains("viewthread") || 
                     href.contains("mod=post") || href.contains("action=")
        return isPureForum && !isJunk
    }

    // 严格标题校验：杜绝“处理举报贴”混入
    private fun isValidForumTitle(title: String): Boolean {
        if (title.isBlank() || title.length < 2 || title.length > 15) return false
        if (title.toIntOrNull() != null) return false
        if (BLACKLIST.any { title.contains(it) }) return false
        return true
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
            title.contains("安全") || title.contains("逆向") || title.contains("病毒") || title.contains("破解") || title.contains("软件") -> R.drawable.ic_sercurite
            title.contains("程序") || title.contains("工具") || title.contains("资源") || title.contains("原创") -> R.drawable.ic_software
            title.contains("水") || title.contains("闲聊") || title.contains("福利") -> R.drawable.ic_water
            else -> R.drawable.ic_creator
        }
    }
}
