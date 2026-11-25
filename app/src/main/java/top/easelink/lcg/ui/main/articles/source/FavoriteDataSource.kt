package top.easelink.lcg.ui.main.articles.source

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.parseFavorites
import top.easelink.lcg.utils.WebsiteConstant.GET_FAVORITE_QUERY

object FavoriteDataSource {
    suspend fun getAllRemoteFavorites(): List<ArticleEntity> {
        val favorites = mutableListOf<ArticleEntity>()
        var nextPageUrl: String? = GET_FAVORITE_QUERY
        var firstPage = true
        while (nextPageUrl != null) {
            val doc = JsoupClient.sendGetRequestWithQuery(nextPageUrl)
            if (firstPage) {
                firstPage = false
                // 只有"未登录 / 站点报错"才判定为无法同步；
                // 收藏容器 id 变化不应被当成空列表或异常，否则一次同步就会清空本地。
                val message = doc.getElementById("messagetext")?.text().orEmpty()
                val needLogin = doc.getElementById("messagelogin") != null ||
                    message.contains("请先登录") || message.contains("您需要先登录") ||
                    message.contains("登录已失效") || message.contains("尚未登录")
                if (needLogin) throw LoginRequiredException()
                if (doc.select(".alert_error").isNotEmpty()) {
                    throw IllegalStateException("读取网站收藏失败: $message")
                }
                // 既没有收藏容器、也没有"收藏"字样，说明拿到的不是收藏页（被拦截/跳转），
                // 此时绝不能当作"零条收藏"去覆盖本地。
                val looksLikeFavoritePage = doc.getElementById("favorite_ul") != null ||
                    doc.select("a.y[href*=favorite]").isNotEmpty() ||
                    doc.text().contains("收藏")
                if (!looksLikeFavoritePage) {
                    throw IllegalStateException("网站收藏页无法识别，可能被拦截，请稍后重试")
                }
            }
            val pageItems = parseFavorites(doc)
            Timber.d("【收藏】第 %d 页解析到 %d 条", favorites.size + 1, pageItems.size)
            favorites.addAll(pageItems)
            nextPageUrl = doc.selectFirst("a.nxt")?.attr("href")
        }
        return favorites
    }
}
