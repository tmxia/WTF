package top.easelink.lcg.ui.main.source.remote

import android.util.ArrayMap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import timber.log.Timber
import top.easelink.framework.threadpool.BackGroundPool
import top.easelink.lcg.cache.PreviewCacheManager
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.network.NetworkException as NetException
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.ArticlesDataSource
import top.easelink.lcg.ui.main.source.FavoritesRemoteDataSource
import top.easelink.lcg.ui.main.source.local.ArticlesDatabase
import top.easelink.lcg.ui.main.source.model.*
import top.easelink.lcg.utils.WebsiteConstant.ADD_TO_FAVORITE_QUERY
import top.easelink.lcg.utils.WebsiteConstant.FORUM_BASE_QUERY
import top.easelink.lcg.utils.WebsiteConstant.REMOVE_FAVORITE_QUERY
import java.net.SocketTimeoutException
import java.util.*

object ArticlesRemoteDataSource : ArticlesDataSource, FavoritesRemoteDataSource {
    private const val USER_NAME = "name"
    private const val USER_AVATAR = "avatar"
    private const val USER_PROFILE_URL = "profile_url"
    private const val USER_EXTRA_INFO = "extra_info"
    private const val FOLLOW_URL = "follow_url"
    private const val FOLLOW_TITLE = "follow_title"

    private val gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    private val dataSourceScope = CoroutineScope(SupervisorJob() + BackGroundPool)

    @Throws(LoginRequiredException::class, SocketTimeoutException::class)
    override suspend fun getForumArticles(query: String, processThreadList: Boolean): ForumPage? = processForumArticlesDocument(
        JsoupClient.sendGetRequestWithQuery(query),
        processThreadList
    )

    override suspend fun getHomePageArticles(param: String, pageNum: Int): List<Article> = try {
        getArticles("$FORUM_BASE_QUERY$param&page=$pageNum")
    } catch (_: Exception) {
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
                    val userInfo = userInfos.getOrNull(i) ?: emptyMap()
                    
                    val post = Post(
                        author = userInfo[USER_NAME].orEmpty().ifBlank { "匿名" },
                        avatar = userInfo[USER_AVATAR].orEmpty(),
                        // 安全索引访问，若缺失则提供默认值
                        date = dateTimes.getOrNull(i).orEmpty(),
                        content = contents.getOrNull(i).orEmpty(),
                        replyUrl = replyUrls.getOrNull(i),
                        replyAddUrl = replyAddUrls.getOrNull(i),
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

    private const val HOT_PATTERN = "热度"
    private const val RECOMMENDED_PATTERN = "评价指数"
    private const val HOT_LIMIT = 100
    private const val RECOMMENDED_LIMIT = 10

    private suspend fun getArticles(query: String): List<Article> {
        return try {
            val doc = JsoupClient.sendGetRequestWithQuery(query)
            val rows = buildList {
                addAll(doc.select("tbody[id^=normal]"))
                if (isEmpty()) addAll(doc.select("tbody[id^=normalthread]"))
                if (isEmpty()) addAll(doc.select("table tbody tr"))
            }
            rows.mapNotNull { e -> extractArticleFromElement(e) }
        } catch (e: Exception) {
            Timber.e(e)
            emptyList()
        }
    }

    @Throws(LoginRequiredException::class)
    private fun processForumArticlesDocument(doc: Document, processThreadList: Boolean): ForumPage? {
        try {
            var elements = doc.select("tbody[id^=normal]")
            if (elements.isEmpty()) elements = doc.select("tbody[id^=normalthread]")
            if (elements.isEmpty()) elements = doc.select("table tbody tr")

            if (elements.isEmpty()) {
                val messageText = doc.getElementById("messagetext")?.text().orEmpty()
                Timber.tag("[ForumParse]").w("未匹配到帖子节点, messagetext=$messageText, title=${doc.title()}")
                return ForumPage(emptyList(), emptyList())
            }
            Timber.tag("[ForumParse]").d("匹配帖子节点 ${elements.size} 个")

            val articleList: List<Article> = elements.mapNotNull { e -> extractArticleFromElement(e) }

            if (articleList.isEmpty()) {
                Timber.tag("[ForumParse]").w("节点命中 ${elements.size} 个但解析出 0 条帖子")
                return ForumPage(emptyList(), emptyList())
            }

            var threadList: List<ForumThread>? = null
            if (processThreadList) {
                doc.getElementById("thread_types")?.let { threadTypes ->
                    threadList = threadTypes.getElementsByTag("li").mapNotNull { elementByTag ->
                        try {
                            val element = elementByTag.getElementsByTag("a").firstOrNull()
                            val tempElements = element?.getElementsByTag("span") ?: Elements()
                            if (tempElements.isNotEmpty()) tempElements.remove()
                            val threadUrl = element?.attr("href").orEmpty()
                            val name = element?.text()?.trim { it <= ' ' }.orEmpty()
                            if (name.isNotEmpty() && threadUrl.isNotEmpty()) ForumThread(name, threadUrl) else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            return ForumPage(articleList, threadList ?: emptyList())
        } catch (e: LoginRequiredException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }

    /**
     * [ARCH-PHASE-11] 统一的防腐解析器：从单个 DOM 元素中提取 Article 对象。
     * 即使部分字段解析失败，也不会影响整条帖子展示。
     */
    private fun extractArticleFromElement(e: Element): Article? {
        return runCatching {
            val title = e.selectFirst("th.common > .xst")?.text().orEmpty()
                .ifBlank { e.selectFirst("th.new > .xst")?.text().orEmpty() }
                .ifBlank { e.selectFirst("a.xst")?.text().orEmpty() }
                .ifBlank { e.selectFirst("th a[href*=viewthread]")?.text().orEmpty() }
                
            val url = extractAttrFrom(e, "href", "th.common", "a.xst")
                .ifBlank { extractAttrFrom(e, "href", "th.new", "a.xst") }
                .ifBlank { e.selectFirst("a.xst")?.attr("href").orEmpty() }
                .ifBlank { e.selectFirst("th a[href*=viewthread]")?.attr("href").orEmpty() }

            if (title.isBlank() || url.isBlank()) return null

            val reply = extractFrom(e, "td.num", "a.xi2").filter { it.isDigit() }.toIntOrNull() ?: 0
            val view = extractFrom(e, "td.num", "em").filter { it.isDigit() }.toIntOrNull() ?: 0
            val author = extractFrom(e, "td.by", "a[href*=uid]").ifBlank { "匿名" }
            val date = extractFrom(e, "td.by", "span")
            val origin = e.selectFirst("td.by > a[target]")?.text().orEmpty()

            val helpInfo = e.select("th.common > span.xi1 > span.xw1").firstOrNull()?.text().orEmpty()
            var helpCoin = 0
            if (helpInfo.isEmpty()) {
                if (e.selectFirst("th.common")?.text()?.contains("- [已解决]") == true) helpCoin = -1
            } else {
                helpCoin = helpInfo.toIntOrNull() ?: 0
            }

            val isRecommended = if (AppConfig.articleShowRecommendFlag) {
                e.selectFirst("th.common")?.getElementsByTag("img")?.map { it.attr("title") }?.any { s ->
                    when {
                        s.contains(HOT_PATTERN) -> s.replace(HOT_PATTERN, "").trim().toIntOrNull()?.let { it >= HOT_LIMIT } ?: false
                        s.contains(RECOMMENDED_PATTERN) -> s.replace(RECOMMENDED_PATTERN, "").trim().toIntOrNull()?.let { it >= RECOMMENDED_LIMIT } ?: false
                        else -> false
                    }
                } ?: false
            } else false

            Article(title, author, date, url, view, reply, origin, helpCoin, isRecommended)
        }.onFailure {
            Timber.tag("[ForumParse]").e(it, "解析单条帖子异常，已跳过")
        }.getOrNull()
    }

    private fun extractFrom(element: Element, vararg tags: String): String {
        if (tags.isNullOrEmpty()) return element.text()
        var e: Elements? = Elements(element)
        for (tag in tags) {
            e = e?.select(tag)
            if (e.isNullOrEmpty()) break
        }
        return e?.text().orEmpty()
    }

    private fun extractAttrFrom(element: Element, attr: String, vararg tags: String): String {
        if (tags.isNullOrEmpty()) return element.text()
        var e: Elements? = Elements(element)
        for (tag in tags) {
            e = e?.select(tag)
            if (e.isNullOrEmpty()) break
        }
        return e?.attr(attr).orEmpty()
    }

    private fun getReplyAddUrl(document: Document): List<String> {
        val list: MutableList<String> = ArrayList(12)
        try {
            document.getElementById("recommend_add")?.attr("href")?.let { list.add(it) }
            document.select("a.replyadd").forEach { list.add(it.attr("href")) }
        } catch (_: NullPointerException) { }
        return list
    }

    private fun getUserInfo(document: Document): List<Map<String, String>> {
        val list: MutableList<Map<String, String>> = ArrayList(12)
        val elements = document.select("td[rowspan]")
        for (element in elements) {
            val userInfoMap: MutableMap<String, String> = ArrayMap(4)
            element.select("div.avatar").apply {
                userInfoMap[USER_AVATAR] = select("img").attr("src")
                userInfoMap[USER_PROFILE_URL] = select("a").attr("href")
            }
            element.selectFirst("a[id^=follow]")?.let {
                userInfoMap[FOLLOW_URL] = it.attr("href")
                userInfoMap[FOLLOW_TITLE] = it.attr("title")
            }
            userInfoMap[USER_EXTRA_INFO] = element.getElementsByTag("dl").outerHtml()
            userInfoMap[USER_NAME] = element.select("a.xw1").text()
            list.add(userInfoMap)
        }
        return list
    }

    private fun getDateTime(document: Document): List<String> = document.select("div.authi").select("em").map { it.text() }

    @Throws(BlockException::class)
    private fun getFirstPost(document: Document): PreviewPost {
        val dateTime = document.selectFirst("div.authi")?.select("em")?.text() ?: run {
            val message = document.getElementById("messagetext")?.text().orEmpty()
            throw BlockException(message)
        }
        val content = getFirstContent(document)
        var avatar: String? = null
        var name: String? = null
        document.selectFirst("td[rowspan]")?.apply {
            avatar = selectFirst("div.avatar")?.selectFirst("img")?.attr("src")
            name = selectFirst("a.xw1")?.text()
        }
        return PreviewPost(
            avatar = avatar ?: "",
            author = name ?: "Unknown",
            date = dateTime,
            content = content
        )
    }

    private fun getFirstContent(doc: Document): String = doc.selectFirst("div.pcb").let { element ->
        element?.selectFirst("td.t_f")?.let { tmp -> processContentElement(tmp).html() }
            ?: element?.selectFirst("div.locked")?.html().orEmpty()
    }.orEmpty()

    private fun getContents(doc: Document): List<String> = doc.select("div.pcb").filterNotNull().mapTo(ArrayList<String>()) {
        it.selectFirst("td.t_f")?.let { tmp -> processContentElement(tmp) }?.let { res ->
            it.select("div.savephotop > img")?.forEach { imgElement ->
                res.appendChild(imgElement.attr("src", imgElement.attr("file")))
            }
            res.html()
        } ?: it.selectFirst("div.locked")?.html().orEmpty()
    }

    private fun getReplyUrls(doc: Document): List<String> = doc.getElementsByClass("fastre").map { it.attr("href") }

    private fun processContentElement(element: Element): Element {
        element.select("div.tip").remove()
        element.select("script").remove()
        for (quote in element.select("div.quote")) {
            val inner = quote.selectFirst("blockquote")?.html().orEmpty()
            if (inner.isNotEmpty()) {
                quote.html("<blockquote style=\"border-left:3px solid #ccc;padding-left:8px;color:#888\">$inner</blockquote>")
            }
        }
        for (e in element.getElementsByTag("pre")) {
            val s = e.html().replace("\r\n", "<br/>").replace("\r", "<br/>").replace("\n", "<br/>").replace(" ", "&nbsp;")
            e.html(s)
        }
        val imgElements = element.getElementsByTag("img")
        for (i in imgElements.indices) {
            val imgElement = imgElements[i]
            val src = imgElement.attr("src")
            if (src.contains("static.52pojie.cn/static/image/") && !src.contains("smiley") && !src.contains("none")) {
                imgElement.remove()
            }
            val realAttr = listOf("file", "zoomfile", "data-src", "data-original").firstOrNull { key ->
                val value = imgElement.attr(key)
                value.isNotEmpty() && !value.contains("none.gif")
            }
            if (realAttr != null) imgElement.attr("src", imgElement.attr(realAttr))
        }
        for (bold in element.select("b, strong")) bold.unwrap()
        for (heading in element.select("h1, h2, h3, h4, h5, h6")) heading.tagName("p")
        return element
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

    suspend fun replyAdd(query: String): String = try {
        val doc = JsoupClient.sendGetRequestWithQuery(query)
        doc.getElementsByClass("nfl").firstOrNull()?.text().orEmpty()
    } catch (e: Exception) {
        Timber.e(e)
        "Error"
    }
}
