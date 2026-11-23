package top.easelink.lcg.ui.main.articles.source

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.parseFavorites
import top.easelink.lcg.utils.WebsiteConstant.GET_FAVORITE_QUERY

object FavoriteDataSource {
    suspend fun getAllRemoteFavorites(): List<ArticleEntity> {
        val favorites = mutableListOf<ArticleEntity>()
        var nextPageUrl: String? = GET_FAVORITE_QUERY
        while (nextPageUrl != null) {
            val doc = JsoupClient.sendGetRequestWithQuery(nextPageUrl)
            favorites.addAll(parseFavorites(doc))
            nextPageUrl = doc.selectFirst("a.nxt")?.attr("href")
        }
        return favorites
    }
}
