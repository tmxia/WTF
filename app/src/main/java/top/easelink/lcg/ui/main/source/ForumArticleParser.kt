package top.easelink.lcg.ui.main.source

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import timber.log.Timber
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumPage
import top.easelink.lcg.ui.main.source.model.ForumThread

/**
 * 板块/首页文章列表解析收口：纯 DOM -> model 映射。
 * 站点改版时只需调整本文件选择器，远程数据源的请求与异常编排不动。
 */

private const val HOT_PATTERN = "热度"
private const val RECOMMENDED_PATTERN = "评价指数"
private const val HOT_LIMIT = 100
private const val RECOMMENDED_LIMIT = 10

@Throws(LoginRequiredException::class)
fun parseForumPage(doc: Document, processThreadList: Boolean): ForumPage? {
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

fun parseArticleList(doc: Document): List<Article> {
    val rows = buildList {
        addAll(doc.select("tbody[id^=normal]"))
        if (isEmpty()) addAll(doc.select("tbody[id^=normalthread]"))
        if (isEmpty()) addAll(doc.select("table tbody tr"))
    }
    return rows.mapNotNull { e -> extractArticleFromElement(e) }
}

/**
 * 统一的防腐解析器：从单个 DOM 元素中提取 Article 对象。
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
