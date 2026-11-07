package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        val uniqueUrls = mutableSetOf<String>()

        try {
            Timber.d("【板块API】开始请求 forum.php 获取完整的分类板块树...")
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")

            // Discuz! 标准的分类容器结构：<div id="category_1" class="category"> 或 <div class="category">
            val categories = doc.select("div[id^=category_], div.category")
            Timber.d("【板块API】找到 ${categories.size} 个大分类容器")

            for (category in categories) {
                // 获取分类名称（如“软件安全”、“病毒分析”）
                val categoryName = category.selectFirst(".category-header, .mtm, h2")?.text()?.trim() ?: ""
                
                val dls = category.select("dl")
                for (dl in dls) {
                    val mainLink = dl.selectFirst("dt a")
                    val mainTitleRaw = mainLink?.text()
                    val mainUrl = mainLink?.attr("href")

                    if (!mainTitleRaw.isNullOrBlank() && !mainUrl.isNullOrBlank() && isForumUrl(mainUrl)) {
                        val cleanTitle = cleanTitle(mainTitleRaw)
                        if (uniqueUrls.add(mainUrl)) {
                            list.add(
                                ForumNavigationModel(
                                    title = cleanTitle,
                                    drawableRes = getIconForForum(cleanTitle),
                                    url = mainUrl,
                                    isSubForum = false,
                                    parentTitle = categoryName.ifBlank { null }
                                )
                            )
                        }
                    }

                    // 提取子版块
                    val subLinks = dl.select("dd a")
                    for (subLink in subLinks) {
                        val subTitleRaw = subLink.text()
                        val subUrl = subLink.attr("href")
                        if (subTitleRaw.isNotBlank() && subUrl.isNotBlank() && isForumUrl(subUrl)) {
                            val cleanSubTitle = cleanTitle(subTitleRaw)
                            if (uniqueUrls.add(subUrl)) {
                                list.add(
                                    ForumNavigationModel(
                                        title = cleanSubTitle,
                                        drawableRes = getIconForForum(cleanSubTitle),
                                        url = subUrl,
                                        isSubForum = true,
                                        parentTitle = cleanTitle(mainTitleRaw!!)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 如果分类容器解析不到（WAF 拦截或者页面改版），回退到最原始的全局 <dl> 扫描
            if (list.isEmpty()) {
                Timber.w("【板块API】分类容器解析为空，启动兜底全局 <dl> 扫描...")
                val dls = doc.select("dl")
                for (dl in dls) {
                    val mainLink = dl.selectFirst("dt a")
                    val mainTitleRaw = mainLink?.text()
                    val mainUrl = mainLink?.attr("href")
                    if (!mainTitleRaw.isNullOrBlank() && !mainUrl.isNullOrBlank() && isForumUrl(mainUrl)) {
                        val cleanTitle = cleanTitle(mainTitleRaw)
                        if (uniqueUrls.add(mainUrl)) {
                            list.add(ForumNavigationModel(cleanTitle, getIconForForum(cleanTitle), mainUrl, false))
                        }
                    }
                }
            }

            Timber.d("【板块API】抓取完成，最终确认 ${list.size} 个真实板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求异常")
        }
        return list
    }

    private fun isForumUrl(url: String): Boolean {
        return url.matches(Regex("forum-\\d+-1\\.html")) || (url.startsWith("forum-") && !url.contains("viewthread"))
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace("『", "").replace("』", "")
            .replace("《", "").replace("》", "")
            .replace("【", "").replace("】", "")
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
