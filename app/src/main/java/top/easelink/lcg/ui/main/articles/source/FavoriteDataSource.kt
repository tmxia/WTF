package top.easelink.lcg.ui.main.articles.source

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.parseFavorites
import top.easelink.lcg.utils.WebsiteConstant.GET_FAVORITE_QUERY

object FavoriteDataSource {
    suspend fun getAllRemoteFavorites(): List<ArticleEntity> {
        val favorites = mutableListOf<ArticleEntity>()
        var nextPageUrl: String? = GET_FAVORITE_QUERY
        var firstPageParsed = false
        while (nextPageUrl != null) {
            val doc = JsoupClient.sendGetRequestWithQuery(nextPageUrl)
            // 收藏列表容器缺失 = 被盾/登录失效/页面异常，不能当"收藏为空"处理，
            // 否则一次同步就会把本地收藏清空。
            if (!firstPageParsed && doc.getElementById("favorite_ul") == null) {
                throw IllegalStateException(
                    "无法读取网站收藏列表: " +
                        doc.getElementById("messagetext")?.text().orEmpty()
                )
            }
            firstPageParsed = true
            favorites.addAll(parseFavorites(doc))
            nextPageUrl = doc.selectFirst("a.nxt")?.attr("href")
        }
        return favorites
    }
}
