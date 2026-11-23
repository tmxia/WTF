package top.easelink.lcg.ui.search.source

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import timber.log.Timber
import top.easelink.lcg.ui.search.model.BaiduSearchResult
import top.easelink.lcg.ui.search.model.BaiduSearchResults
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.model.LCGSearchResults

/**
 * 搜索域解析收口：纯 DOM -> model 映射，不持有 Service 状态。
 * 站点改版时只需调整本文件选择器，搜索 Service 的请求/重试/缓存/限流逻辑不动。
 */

/** 站内搜索解析结果：items 供渲染，nextPageUrl 供 Service 维护翻页状态。 */
data class LcgSearchParse(
    val items: List<LCGSearchResultItem>,
    val nextPageUrl: String?
)

fun parseLcgSearch(doc: Document): LcgSearchParse {
    val results: List<LCGSearchResultItem> = try {
        doc.getElementsByClass("pbw").mapNotNull {
            try {
                val spans = it.getElementsByTag("span")
                val aNode = it.selectFirst("a")
                val pNode = it.selectFirst("p.xg1")

                if (aNode != null && pNode != null && spans.size >= 3) {
                    LCGSearchResultItem(
                        title = aNode.html(),
                        url = aNode.attr("href"),
                        replyView = pNode.text(),
                        content = pNode.nextElementSibling()?.html().orEmpty(),
                        author = spans[1].text(),
                        date = spans[0].text(),
                        forum = spans[2].text()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    } catch (e: Exception) {
        Timber.tag("SearchDebug").e(e, "搜索结果解析异常，返回已提取部分")
        emptyList()
    }

    val nextPageUrl = try {
        doc.selectFirst("a.nxt")?.attr("href")
    } catch (e: Exception) {
        Timber.w(e)
        null
    }
    return LcgSearchParse(results, nextPageUrl)
}

fun parseBaiduResults(doc: Document): BaiduSearchResults {
    val list: List<BaiduSearchResult> = doc.select("div.result")
        ?.map {
            try {
                val title = extractFrom(it, "h3.c-title", "a")
                val url = extractAttrFrom(it, "href", "h3.c-title", "a")
                val content = extractFrom(it, "div.c-abstract")
                return@map BaiduSearchResult(title, content, url)
            } catch (nbe: NumberFormatException) {
                Timber.v(nbe)
            } catch (e: Exception) {
                Timber.e(e)
            }
            null
        }
        ?.filterNotNull()
        .orEmpty()

    if (list.isEmpty()) {
        return BaiduSearchResults(emptyList())
    }
    return BaiduSearchResults(list).also {
        try {
            it.nextPageUrl = doc.selectFirst("a.pager-next-foot")?.attr("href")
            it.totalResult = doc.getElementsByClass("support-text-top").firstOrNull()?.text()
        } catch (e: Exception) {
            it.nextPageUrl = null
        }
    }
}

private fun extractFrom(element: Element, vararg tags: String): String {
    if (tags.isNullOrEmpty()) {
        return element.text()
    }
    var e = Elements(element)
    for (tag in tags) {
        e = e.select(tag)
        if (e.isEmpty()) {
            break
        }
    }
    return e.text()
}

private fun extractAttrFrom(element: Element, attr: String, vararg tags: String): String {
    if (tags.isNullOrEmpty()) {
        return element.text()
    }
    var e = Elements(element)
    for (tag in tags) {
        e = e.select(tag)
        if (e.isEmpty()) {
            break
        }
    }
    return e.attr(attr)
}
