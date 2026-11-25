package top.easelink.lcg.ui.main.source.repository

import top.easelink.lcg.ui.main.articles.source.FavoriteDataSource
import top.easelink.lcg.ui.main.source.local.ArticlesLocalDataSource
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ArticleDetail
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.LikeActionResult
import top.easelink.lcg.ui.main.source.model.ForumPage
import top.easelink.lcg.ui.main.source.model.PreviewPost
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource
import top.easelink.lcg.ui.main.source.FavoriteSnapshot

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

    suspend fun replyAdd(url: String): LikeActionResult =
        ArticlesRemoteDataSource.replyAdd(url)

    // ---- 收藏远程 ----

    suspend fun addFavoriteRemote(threadId: String, formHash: String): Boolean =
        ArticlesRemoteDataSource.addFavorites(threadId, formHash)

    suspend fun removeFavoriteRemote(threadId: String, formHash: String): Boolean =
        ArticlesRemoteDataSource.removeFavorites(threadId, formHash)

    suspend fun getRemoteFavorites(): List<ArticleEntity> =
        FavoriteDataSource.getAllRemoteFavorites().also { list ->
            // 拉取远端收藏的同时刷新快照，供详情页判断收藏状态
            FavoriteSnapshot.update(list.map { it.url })
        }

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

    /**
     * 以网站收藏为准：远端拉取成功时整体覆盖本地，网站侧删除的条目才会一并消失。
     * 本地新增、尚未成功同步到网站（delUrl 为空）的条目保留，避免一次同步把它们清掉。
     */
    suspend fun syncRemoteFavorites(): List<ArticleEntity> {
        val remote = getRemoteFavorites()
        val localOnly = getAllLocalFavorites()
            .filter { it.delUrl.isBlank() && remote.none { r -> r.url == it.url } }
        delAllLocalFavorites()
        val merged = remote + localOnly
        if (merged.isNotEmpty()) {
            addAllLocalFavorites(merged)
        }
        return getAllLocalFavorites()
    }

    /** 按收藏列表中站点给出的删除链接取消收藏（含 tid/formhash，无需再解析）。 */
    suspend fun removeFavoriteByDelUrl(delUrl: String): Boolean =
        ArticlesRemoteDataSource.removeFavoriteByUrl(delUrl)
}
