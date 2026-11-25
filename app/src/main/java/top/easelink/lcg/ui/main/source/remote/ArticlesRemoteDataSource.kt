package top.easelink.lcg.ui.main.source.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException
import timber.log.Timber
import top.easelink.framework.threadpool.BackGroundPool
import top.easelink.lcg.cache.PreviewCacheManager
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.network.NetworkException as NetException
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.ArticlesDataSource
import top.easelink.lcg.ui.main.source.FavoritesRemoteDataSource
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ArticleAbstractResponse
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.main.source.model.ArticleDetail
import top.easelink.lcg.ui.main.source.model.HistoryEntity
import top.easelink.lcg.ui.main.source.model.PreviewPost
import top.easelink.lcg.ui.main.source.USER_AVATAR
import top.easelink.lcg.ui.main.source.USER_EXTRA_INFO
import top.easelink.lcg.ui.main.source.USER_NAME
import top.easelink.lcg.ui.main.source.USER_PROFILE_URL
import top.easelink.lcg.ui.main.source.FOLLOW_TITLE
import top.easelink.lcg.ui.main.source.FOLLOW_URL
import top.easelink.lcg.ui.main.source.getContents
import top.easelink.lcg.ui.main.source.getDateTime
import top.easelink.lcg.ui.main.source.getFirstPost
import top.easelink.lcg.ui.main.source.getUserInfo
import top.easelink.lcg.ui.main.source.getPostIds
import top.easelink.lcg.ui.main.source.getReplyAddUrl
import top.easelink.lcg.ui.main.source.getReplyAddUrlMap
import top.easelink.lcg.ui.main.source.getReplyUrls
import top.easelink.lcg.ui.main.source.local.ArticlesDatabase
import top.easelink.lcg.utils.WebsiteConstant.ADD_TO_FAVORITE_QUERY
import top.easelink.lcg.utils.WebsiteConstant.FORUM_BASE_QUERY
import top.easelink.lcg.utils.WebsiteConstant.REMOVE_FAVORITE_QUERY
import java.net.SocketTimeoutException
import java.util.ArrayList

object ArticlesRemoteDataSource : ArticlesDataSource, FavoritesRemoteDataSource {
    private val gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    private val dataSourceScope = CoroutineScope(SupervisorJob() + BackGroundPool)

    @Throws(LoginRequiredException::class, SocketTimeoutException::class)
    override suspend fun getForumArticles(query: String, processThreadList: Boolean) =
        top.easelink.lcg.ui.main.source.parseForumPage(
            JsoupClient.sendGetRequestWithQuery(query),
            processThreadList
        )

    override suspend fun getHomePageArticles(param: String, pageNum: Int): List<Article> = try {
        val doc = JsoupClient.sendGetRequestWithQuery("$FORUM_BASE_QUERY$param&page=$pageNum")
        top.easelink.lcg.ui.main.source.parseArticleList(doc)
    } catch (e: Exception) {
        Timber.e(e)
        emptyList()
    }

    @Throws(BlockException::class, HttpStatusException::class)
    override suspend fun getPostPreview(query: String): PreviewPost? {
        return try {
            val doc = PreviewCacheManager.findDocOrNull(query) ?: run {
                val freshDoc = JsoupClient.sendGetRequestWithQuery(query)
                if (freshDoc.selectFirst("div.pcb") == null && freshDoc.select(".alert_error").isNotEmpty()) {
                    return null
                }
                PreviewCacheManager.saveToDisk(query, freshDoc.html())
                freshDoc
            }
            getFirstPost(doc)
        } catch (e: HttpStatusException) {
            if (e.statusCode == 404) null else throw e
        } catch (e: BlockException) {
            if (e.message?.contains("删除") == true) null else throw e
        } catch (_: Exception) {
            null
        }
    }

    @Throws(BlockException::class, NetException::class)
    override suspend fun getArticleDetail(query: String, isFirstFetch: Boolean): ArticleDetail? {
        try {
            val doc = JsoupClient.sendGetRequestWithQuery(query)
            val articleAbstract: ArticleAbstractResponse? = doc.selectFirst("script")?.let {
                try {
                    val json = it.html().trim().replace("\u00a0".toRegex(), "")
                    return@let gson.fromJson(json, ArticleAbstractResponse::class.java)
                } catch (_: Exception) { }
                null
            }

            val title = doc.selectFirst("span#thread_subject")?.text().orEmpty()
            if (title.isEmpty()) {
                val message = doc.getElementById("messagetext")?.text().orEmpty()
                throw BlockException(message)
            }

            val nextPageUrl = doc.selectFirst("a.nxt")?.attr("href").orEmpty()
            val replyAddUrls = getReplyAddUrl(doc)
            val postIds = getPostIds(doc)
            val replyAddUrlMap = getReplyAddUrlMap(doc)
            val userInfos = getUserInfo(doc)
            val contents = getContents(doc)

            if (isFirstFetch) {
                dataSourceScope.launch {
                    addToHistory(
                        url = query,
                        title = title,
                        author = userInfos.getOrNull(0)?.getOrElse(USER_NAME) { "" } ?: "",
                    )
                }
            }

            val dateTimes = getDateTime(doc)
            val replyUrls = getReplyUrls(doc)
            val postList: MutableList<Post> = ArrayList(userInfos.size)

            for (i in userInfos.indices) {
                // [ARCH-PHASE-16] 使用 runCatching 兜底，并将数组访问改为安全的 getOrNull
                runCatching {
                    val userInfo = userInfos.getOrNull(i) ?: emptyMap<String, String>()

                    val post = Post(
                        author = userInfo[USER_NAME].orEmpty().ifBlank { "匿名" },
                        avatar = userInfo[USER_AVATAR].orEmpty(),
                        // 安全索引访问，若缺失则提供默认值
                        date = dateTimes.getOrNull(i).orEmpty(),
                        content = contents.getOrNull(i).orEmpty(),
                        replyUrl = replyUrls.getOrNull(i),
                        // 优先按 pid 精确归属；容器缺失时回退旧的顺序列表
                        pid = postIds.getOrNull(i),
                        replyAddUrl = postIds.getOrNull(i)?.let { replyAddUrlMap[it] }
                            ?: replyAddUrls.getOrNull(i),
                        profileUrl = userInfo[USER_PROFILE_URL].orEmpty(),
                        extraInfo = userInfo[USER_EXTRA_INFO],
                        followInfo = userInfo[FOLLOW_TITLE]?.to(userInfo[FOLLOW_URL] ?: "")
                    )
                    postList.add(post)
                }.onFailure {
                    // 即使某楼层 DOM 结构被完全破坏，也只打日志跳过，绝不崩溃
                    Timber.tag("[ArticleParse]").w(it, "解析第 $i 楼数据失败，已跳过该楼层")
                }
            }

            val fromHash = doc.selectFirst("input[name=formhash]")?.attr("value")
            return ArticleDetail(title, postList, nextPageUrl, fromHash, articleAbstract)
        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException -> throw NetException.TimeoutError("请求超时", e)
                is BlockException -> throw e
                else -> throw e
            }
        }
    }

    private suspend fun addToHistory(url: String, title: String, author: String, content: String? = null) {
        ArticlesDatabase.getInstance().articlesDao().insertHistory(
            HistoryEntity(
                url = url,
                title = title,
                author = author,
                content = content.orEmpty(),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun addFavorites(threadId: String, formHash: String): Boolean = try {
        JsoupClient.sendGetRequestWithQuery(String.format(ADD_TO_FAVORITE_QUERY, threadId, formHash))
        true
    } catch (e: Exception) {
        Timber.e(e)
        false
    }

    override suspend fun removeFavorites(threadId: String, formHash: String): Boolean = try {
        val doc = JsoupClient.sendGetRequestWithQuery(String.format(REMOVE_FAVORITE_QUERY, threadId, formHash))
        val msg = doc.getElementById("messagetext")?.text().orEmpty()
        val hasError = doc.select(".alert_error").isNotEmpty()
        !hasError && (msg.isEmpty() || msg.contains("成功") || msg.contains("删除"))
    } catch (e: Exception) {
        Timber.e(e)
        false
    }

    /** 用收藏条目自带的删除链接取消收藏，避免再拼 tid/formhash 出错。 */
    suspend fun removeFavoriteByUrl(delUrl: String): Boolean = try {
        val doc = JsoupClient.sendGetRequestWithQuery(delUrl)
        val msg = doc.getElementById("messagetext")?.text().orEmpty()
        doc.select(".alert_error").isEmpty() &&
            (msg.isEmpty() || msg.contains("成功") || msg.contains("删除") || msg.contains("取消"))
    } catch (e: Exception) {
        Timber.e(e)
        false
    }

    suspend fun replyAdd(query: String): String = try {
        val doc = JsoupClient.sendGetRequestWithQuery(query)
        doc.getElementsByClass("nfl").firstOrNull()?.text().orEmpty()
    } catch (e: Exception) {
        Timber.e(e)
        "Error"
    }
}
