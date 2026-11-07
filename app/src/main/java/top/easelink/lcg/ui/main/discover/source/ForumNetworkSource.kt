package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    /**
     * 使用 Jsoup 从论坛首页抓取全量主/子版块
     */
    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        try {
            // 请求版块导航页
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            
            // Discuz! 标准的版块容器结构：<div id="category_1"> 或 <div class="category">
            val categories = doc.select("div[id^=category_], div.category")
            
            for (category in categories) {
                val dls = category.select("dl")
                for (dl in dls) {
                    // 主版块解析
                    val mainLink = dl.selectFirst("dt a")
                    val mainTitle = mainLink?.text()
                    var mainUrl = mainLink?.attr("href")
                    
                    if (!mainTitle.isNullOrBlank() && !mainUrl.isNullOrBlank()) {
                        // 过滤掉没有实际内容的链接
                        if (!mainUrl.startsWith("forum-") && !mainUrl.startsWith("forum.php")) {
                            continue
                        }
                        list.add(
                            ForumNavigationModel(
                                title = mainTitle,
                                drawableRes = R.drawable.ic_software,
                                url = mainUrl,
                                isSubForum = false
                            )
                        )
                    }

                    // 子版块解析
                    val dd = dl.selectFirst("dd")
                    if (dd != null) {
                        val subLinks = dd.select("a")
                        for (subLink in subLinks) {
                            val subTitle = subLink.text()
                            val subUrl = subLink.attr("href")
                            if (subTitle.isNotBlank() && subUrl.isNotBlank()) {
                                list.add(
                                    ForumNavigationModel(
                                        title = subTitle,
                                        drawableRes = R.drawable.ic_water,
                                        url = subUrl,
                                        isSubForum = true,
                                        parentTitle = mainTitle
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Timber.d("API 抓取板块成功，共计 ${list.size} 个")
        } catch (e: Exception) {
            Timber.e(e, "抓取板块失败，将降级使用本地数据")
        }
        return list
    }
}
