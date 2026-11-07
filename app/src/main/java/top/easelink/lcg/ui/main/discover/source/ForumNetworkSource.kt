package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    private val BLACKLIST = listOf(
        "新帖", "新贴", "举报", "投诉", "管理", "公告", "活动", "大赛", "竞赛", 
        "招募", "欢迎", "处理", "站务", "福利", "建议", "申请", "专帖", "置顶", "版规", "指引", "办公区", "证据区"
    )

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            Timber.d("【板块API】开始解析 forum.php ...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            val allLinks = doc.select("a[href*=forum-], a[href*=forumdisplay]")

            for (link in allLinks) {
                val href = link.attr("href").trim()
                val title = link.text().trim()

                if (!isValidForumUrl(href) || !isValidForumTitle(title)) continue

                val standardUrl = if (href.startsWith("http")) href else href
                if (uniqueUrls.add(standardUrl)) {
                    val isSub = link.parents().any { it.tagName() == "dd" }
                    
                    val parentTitle = link.parent()?.parent()?.previousElementSibling()?.selectFirst("dt a")?.text()?.let { cleanTitle(it) }
                    
                    list.add(
                        ForumNavigationModel(
                            title = cleanTitle(title),
                            drawableRes = getIconByFid(extractFid(href), title),
                            url = standardUrl,
                            isSubForum = isSub,
                            parentTitle = parentTitle
                        )
                    )
                }
            }
            Timber.d("【板块API】解析完成，共计 ${list.size} 个板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】解析异常")
        }
        return list
    }

    private fun extractFid(url: String): Int? {
        val match1 = Regex("forum-(\\d+)-1\\.html").find(url)
        val match2 = Regex("fid=(\\d+)").find(url)
        return match1?.groupValues?.get(1)?.toIntOrNull() ?: match2?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun isValidForumUrl(href: String): Boolean {
        val isPureForum = href.matches(Regex("forum-\\d+-1\\.html")) || href.contains("forumdisplay&fid=")
        val isJunk = href.contains("newthread") || href.contains("viewthread") || href.contains("mod=post") ||
                     href.contains("action=") || href.contains("guide") || href.contains("filter=") || 
                     href.contains("view=") || href.contains("typeid=")
        return isPureForum && !isJunk
    }

    private fun isValidForumTitle(title: String): Boolean {
        if (title.isBlank() || title.length < 2 || title.length > 15) return false
        if (title.toIntOrNull() != null) return false
        return BLACKLIST.none { title.contains(it) }
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace("『", "").replace("』", "").replace("《", "").replace("》", "")
            .replace("【", "").replace("】", "").replace("\n", "").replace(" ", "").trim()
    }

    private fun getIconByFid(fid: Int?, title: String): Int {
        return when (fid) {
            2 -> R.drawable.ic_creator      
            16 -> R.drawable.ic_software    
            65 -> R.drawable.ic_sercurite   
            5 -> R.drawable.ic_sercurite    
            4 -> R.drawable.ic_creator      
            24 -> R.drawable.ic_message_black_24dp 
            6 -> R.drawable.ic_message_black_24dp  
            10 -> R.drawable.ic_water       
            66 -> R.drawable.ic_welfare     
            else -> when {
                title.contains("安全") || title.contains("逆向") || title.contains("病毒") || title.contains("破解") -> R.drawable.ic_sercurite
                title.contains("软件") || title.contains("程序") || title.contains("工具") || title.contains("资源") || title.contains("原创") -> R.drawable.ic_software
                title.contains("水") || title.contains("闲聊") || title.contains("福利") -> R.drawable.ic_water
                else -> R.drawable.ic_creator
            }
        }
    }
}
