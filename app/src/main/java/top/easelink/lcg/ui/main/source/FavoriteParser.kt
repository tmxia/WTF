package top.easelink.lcg.ui.main.source

import org.jsoup.nodes.Document
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.utils.toTimeStamp

/**
 * 收藏列表解析收口：纯 DOM -> ArticleEntity 映射。
 */

fun parseFavorites(doc: Document): List<ArticleEntity> = doc.getElementById("favorite_ul")
    ?.children()
    ?.mapNotNull {
        val delUrl = it.selectFirst("a.y")?.attr("href").orEmpty()
        val dateAdded = it.selectFirst("span.xg1")?.text().orEmpty()

        val targetElement = it.getElementsByAttribute("target").firstOrNull()

        if (targetElement != null) {
            val title = targetElement.text().orEmpty()
            val articleUrl = targetElement.attr("href").orEmpty()
            ArticleEntity(
                title = title,
                author = "",
                url = articleUrl,
                content = "",
                timestamp = toTimeStamp(dateAdded),
                delUrl = delUrl
            )
        } else {
            null
        }
    }.orEmpty()
