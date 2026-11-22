package top.easelink.lcg.ui.main.source

import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ArticleDetail
import top.easelink.lcg.ui.main.source.model.ForumPage
import top.easelink.lcg.ui.main.source.model.PreviewPost

interface ArticlesDataSource {
    suspend fun getForumArticles(query: String, processThreadList: Boolean): ForumPage?

    suspend fun getArticleDetail(query: String, isFirstFetch: Boolean): ArticleDetail?

    suspend fun getPostPreview(query: String): PreviewPost?

    suspend fun getHomePageArticles(param: String, pageNum: Int): List<Article>
}
