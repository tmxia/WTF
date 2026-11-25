package top.easelink.lcg.ui.main.source

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.utils.toTimeStamp

/**
 * 收藏列表解析收口：纯 DOM -> ArticleEntity 映射。
 * 容器 id 在不同皮肤/版本上不一致，因此先按 #favorite_ul 取行，
 * 取不到时退化为"带删除链接 a.y 的 li"，避免整页解析不到直接判定为"收藏为空"。
 */
fun parseFavorites(doc: Document): List<ArticleEntity> {
    val rows: List<Element> = doc.getElementById("favorite_ul")?.children()
        ?.takeIf { it.isNotEmpty() }
        ?: doc.select("li").filter { li ->
            li.selectFirst("a.y[href*=favorite]") != null ||
                li.selectFirst("a[href*=op=delete][href*=ac=favorite]") != null
        }
    return rows.mapNotNull { row -> row.toFavoriteEntity() }
}

private fun Element.toFavoriteEntity(): ArticleEntity? {
    val delUrl = (selectFirst("a.y") ?: selectFirst("a[href*=op=delete][href*=ac=favorite]"))
        ?.attr("href")
        .orEmpty()
    val dateAdded = selectFirst("span.xg1")?.text().orEmpty()

    val target = getElementsByAttribute("target").firstOrNull { it.tagName() == "a" }
        ?: selectFirst("a[href*=thread-]")
        ?: selectFirst("a[href*=showthread]")
    val title = target?.text().orEmpty()
    val articleUrl = target?.attr("href").orEmpty()
    if (title.isBlank() || articleUrl.isBlank()) return null

    return ArticleEntity(
        title = title,
        author = "",
        url = articleUrl,
        content = "",
        timestamp = toTimeStamp(dateAdded),
        delUrl = delUrl
    )
}
