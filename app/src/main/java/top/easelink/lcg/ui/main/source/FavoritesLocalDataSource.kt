package top.easelink.lcg.ui.main.source

import top.easelink.lcg.ui.main.source.model.ArticleEntity






interface FavoritesLocalDataSource {
    fun getAllFavoriteArticles(): List<ArticleEntity>
    fun addAllArticleToFavorite(articleEntities: List<ArticleEntity>): Boolean
    fun addArticleToFavorite(articleEntity: ArticleEntity): Boolean
    fun delArticleFromFavorite(id: String): Boolean
    fun delAllArticlesFromFavorite(): Boolean
    fun isArticleInFavorites(id: String): Boolean
}