package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    // 垃圾版块黑名单关键字
    private val BLACKLIST = listOf("大赛", "竞赛", "公告", "活动", "招募", "征集", "专帖")

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            Timber.d("【板块API】开始请求 forum.php ...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")

            // 直接锁定包含板块的 dl 标签，不管是哪个分类下的
            val dls = doc.select("dl")
            Timber.d("【板块API】全页共找到 ${dls.size} 个 <dl> 节点")

            for (dl in dls) {
                // 提取主版块
                val mainLink = dl.selectFirst("dt a")
                val mainTitleRaw = mainLink?.text()?.trim()
                val mainUrl = mainLink?.attr("href")?.trim()

                if (!mainTitleRaw.isNullOrBlank() && !mainUrl.isNullOrBlank()) {
                    val cleanTitle = cleanTitle(mainTitleRaw)
                    if (isValidForum(cleanTitle, mainUrl)) {
                        if (uniqueUrls.add(mainUrl)) {
                            list.add(
                                ForumNavigationModel(
                                    title = cleanTitle,
                                    drawableRes = getIconForForum(cleanTitle),
                                    url = mainUrl,
                                    isSubForum = false
                                )
                            )
                            Timber.d("【板块API】提取主版块: $cleanTitle -> $mainUrl")
                        }
                    }
                }

                // 提取子版块
                val subLinks = dl.select("dd a")
                for (subLink in subLinks) {
                    val subTitleRaw = subLink.text().trim()
                    val subUrl = subLink.attr("href").trim()
                    if (subTitleRaw.isNotBlank() && subUrl.isNotBlank()) {
                        val cleanSubTitle = cleanTitle(subTitleRaw)
                        if (isValidForum(cleanSubTitle, subUrl)) {
                            if (uniqueUrls.add(subUrl)) {
                                list.add(
                                    ForumNavigationModel(
                                        title = cleanSubTitle,
                                        drawableRes = getIconForForum(cleanSubTitle),
                                        url = subUrl,
                                        isSubForum = true,
                                        parentTitle = mainTitleRaw?.let { cleanTitle(it) }
                                    )
                                )
                                Timber.d("【板块API】提取子版块: $cleanSubTitle -> $subUrl")
                            }
                        }
                    }
                }
            }

            Timber.d("【板块API】解析完成，最终确认 ${list.size} 个真实板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求或解析异常")
        }
        return list
    }

    // 判断是否是一个有效且合法的论坛版块
    private fun isValidForum(title: String, url: String): Boolean {
        // 1. 黑名单过滤（剔除大赛、公告等垃圾版块）
        if (BLACKLIST.any { title.contains(it) }) return false

        // 2. URL 格式过滤：只要包含 forum-xx-1.html 或者 forumdisplay&fid= 就算版块
        // 剔除帖子链接 (viewthread)
        if (url.contains("viewthread") || url.contains("mod=post")) return false
        
        val isForumLink = url.contains("forum-") || url.contains("forumdisplay")
        return isForumLink
    }

    // 清理标题中的书名号等无关字符
    private fun cleanTitle(raw: String): String {
        return raw.replace("『", "").replace("』", "")
            .replace("《", "").replace("》", "")
            .replace("【", "").replace("】", "")
            .trim()
    }

    // 智能匹配图标
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
