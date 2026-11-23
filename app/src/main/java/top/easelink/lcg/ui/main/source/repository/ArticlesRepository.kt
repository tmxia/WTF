package top.easelink.lcg.ui.main.source.repository

import top.easelink.lcg.ui.main.articles.source.FavoriteDataSource
import top.easelink.lcg.ui.main.source.local.ArticlesLocalDataSource
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ArticleDetail
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.ForumPage
import top.easelink.lcg.ui.main.source.model.PreviewPost
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource

/**
 * 文章 / 收藏域薄 Repository 门面：组合远程、本地与收藏远程数据源。
 * ViewModel 只依赖本门面，不直接持有 object 数据源；双写判定、UI 状态编排仍留在 ViewModel。
 */
object ArticlesRepository {

    // ---- 远程文章 / 详情 / 预览 ----

    suspend fun getForumArticles(query: String, processThreadList: Boolean): ForumPage? =
        ArticlesRemoteDataSource.getForumArticles(query, processThreadList)

    suspend fun getHomePageArticles(param: String, pageNum: Int): List<Article> =
        ArticlesRemoteDataSource.getHomePageArticles(param, pageNum)

    suspend fun getArticleDetail(query: String, isFirstFetch: Boolean): ArticleDetail? =
        ArticlesRemoteDataSource.getArticleDetail(query, isFirstFetch)

    suspend fun getPostPreview(query: String): PreviewPost? =
        ArticlesRemoteDataSource.getPostPreview(query)

    suspend fun replyAdd(url: String): String =
        ArticlesRemoteDataSource.replyAdd(url)

    // ---- 收藏远程 ----

    suspend fun addFavoriteRemote(threadId: String, formHash: String): Boolean =
        ArticlesRemoteDataSource.addFavorites(threadId, formHash)

    suspend fun removeFavoriteRemote(threadId: String, formHash: String): Boolean =
        ArticlesRemoteDataSource.removeFavorites(threadId, formHash)

    suspend fun getRemoteFavorites(): List<ArticleEntity> =
        FavoriteDataSource.getAllRemoteFavorites()

    // ---- 收藏本地 ----

    suspend fun getAllLocalFavorites(): List<ArticleEntity> =
        ArticlesLocalDataSource.getAllFavoriteArticles()

    suspend fun addAllLocalFavorites(entities: List<ArticleEntity>): Boolean =
        ArticlesLocalDataSource.addAllArticleToFavorite(entities)

    suspend fun addLocalFavorite(entity: ArticleEntity): Boolean =
        ArticlesLocalDataSource.addArticleToFavorite(entity)

    suspend fun delLocalFavorite(id: String): Boolean =
        ArticlesLocalDataSource.delArticleFromFavorite(id)

    suspend fun delAllLocalFavorites(): Boolean =
        ArticlesLocalDataSource.delAllArticlesFromFavorite()

    suspend fun isLocalFavorite(id: String): Boolean =
        ArticlesLocalDataSource.isArticleInFavorites(id)

    /** 远程拉取收藏 → 写本地 → 回读本地，完成一次双写同步。 */
    suspend fun syncRemoteFavorites(): List<ArticleEntity> {
        val remote = getRemoteFavorites()
        if (remote.isNotEmpty()) {
            addAllLocalFavorites(remote)
        }
        return getAllLocalFavorites()
    }
}
