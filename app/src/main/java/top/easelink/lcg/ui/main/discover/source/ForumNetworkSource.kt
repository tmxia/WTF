package top.easelink.lcg.ui.main.discover.source

import top.easelink.lcg.R
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import timber.log.Timber

object ForumNetworkSource {

    fun fetchAllForumsFromApi(): List<ForumNavigationModel> {
        val list = mutableListOf<ForumNavigationModel>()
        try {
            Timber.d("【板块API】开始请求 forum.php...")
            // 请求 52pojie 版块列表页
            val doc = JsoupClient.sendGetRequestWithUrl("https://www.52pojie.cn/forum.php")
            
            // 打印页面标题，如果被 WAF 拦截，标题通常不是“吾爱破解”
            Timber.d("【板块API】请求成功，页面标题: ${doc.title()}")
            
            // 先尝试利用 Discuz! 的 <dl> 结构解析
            val dls = doc.select("dl")
            Timber.d("【板块API】找到 ${dls.size} 个 <dl> 节点")

            for (dl in dls) {
                val mainLink = dl.selectFirst("dt a")
                val mainTitle = mainLink?.text()
                var mainUrl = mainLink?.attr("href")

                if (!mainTitle.isNullOrBlank() && !mainUrl.isNullOrBlank()) {
                    list.add(
                        ForumNavigationModel(
                            title = mainTitle,
                            drawableRes = R.drawable.ic_software,
                            url = mainUrl,
                            isSubForum = false
                        )
                    )
                }

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

            // 如果 <dl> 结构没抓全，进行保底暴力提取所有 forum-xxx-1.html 的链接
            if (list.isEmpty()) {
                Timber.w("【板块API】<dl> 解析为空，启动兜底正则提取...")
                val links = doc.select("a")
                val uniqueUrls = mutableSetOf<String>()
                
                for (link in links) {
                    val title = link.text().trim()
                    val url = link.attr("href").trim()
                    
                    // 52pojie 版块链接格式通常是 forum-xx-1.html
                    if (url.matches(Regex("forum-\\d+-1\\.html")) && title.isNotEmpty() && title.length < 15) {
                        if (uniqueUrls.add(url)) {
                            list.add(
                                ForumNavigationModel(
                                    title = title,
                                    drawableRes = R.drawable.ic_software,
                                    url = url,
                                    isSubForum = false
                                )
                            )
                        }
                    }
                }
                Timber.d("【板块API】兜底提取到 ${list.size} 个板块")
            }

            Timber.d("【板块API】解析完成，最终共计 ${list.size} 个板块")
        } catch (e: Exception) {
            Timber.e(e, "【板块API】请求或解析异常")
        }
        return list
    }
}
