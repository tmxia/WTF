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
            Timber.d("【板块API】开始请求 forum.php 抓取板块结构...")
            // 请求版块导航页
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            
            Timber.d("【板块API】请求成功，页面标题: ${doc.title()}")
            
            // 传统的 Discuz! 结构通常把主版块和子版块放在 <dl> 标签里
            val dls = doc.select("dl")
            Timber.d("【板块API】页面共找到 ${dls.size} 个 <dl> 节点")
            
            for (dl in dls) {
                // 主版块解析
                val mainLink = dl.selectFirst("dt a")
                val mainTitle = mainLink?.text()
                val mainUrl = mainLink?.attr("href")
                
                if (!mainTitle.isNullOrBlank() && !mainUrl.isNullOrBlank()) {
                    list.add(
                        ForumNavigationModel(
                            title = mainTitle,
                            drawableRes = R.drawable.ic_software, // 默认图标
                            url = mainUrl,
                            isSubForum = false
                        )
                    )
                }

                // 子版块解析 (在 <dd> 标签内)
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
            
            // 如果 dl 抓不到，尝试抓取更宽泛的板块链接
            if (list.isEmpty()) {
                Timber.w("【板块API】未从 <dl> 解析到数据，尝试通过 href 特征兜底匹配...")
                val links = doc.select("a[href^=forum-][href$=-1.html]")
                for (link in links) {
                    val title = link.text()
                    val url = link.attr("href")
                    if (title.isNotBlank() && url.isNotBlank()) {
                        list.add(ForumNavigationModel(title, R.drawable.ic_software, url, false))
                    }
                }
            }
            
            Timber.d("【板块API】解析完成，共获取到 ${list.size} 个有效板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】抓取板块发生异常，将降级使用本地写死数据")
        }
        return list
    }
}
