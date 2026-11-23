package top.easelink.lcg.ui.main.source

import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.ui.main.discover.model.RankListModel
import top.easelink.lcg.ui.main.discover.model.RankModel
import top.easelink.lcg.ui.main.discover.source.RankType

/**
 * 发现页（热榜排行）解析收口：纯 DOM -> model 映射。
 */

fun parseRankModelInfo(document: Document, rankType: RankType): RankListModel {
    val time = document.selectFirst("div.notice")?.text().orEmpty()

    val rows = buildList {
        addAll(document.select("tbody[id^=normal]"))
        if (isEmpty()) addAll(document.select("tbody[id^=normalthread]"))
        if (isEmpty()) addAll(document.select("table tbody tr"))
    }

    val list = rows.mapIndexed { index, tr ->
        try {
            val title = tr.selectFirst("th.common > .xst")?.text()
                ?: tr.selectFirst("th.new > .xst")?.text()
                ?: tr.selectFirst("a.xst")?.text()
                ?: tr.selectFirst("th a[href*=viewthread]")?.text()
                ?: ""
            val url = tr.selectFirst("th.common a.xst")?.attr("href")
                ?: tr.selectFirst("th.new a.xst")?.attr("href")
                ?: tr.selectFirst("a.xst")?.attr("href")
                ?: tr.selectFirst("th a[href*=viewthread]")?.attr("href")
                ?: ""
            if (title.isBlank() || url.isBlank()) return@mapIndexed null

            val author = tr.selectFirst("td.by a[href*=uid]")?.text().orEmpty()
            val authorUrl = tr.selectFirst("td.by a[href*=uid]")?.attr("href").orEmpty()
            val date = tr.selectFirst("td.by span")?.text().orEmpty()
            val forum = tr.selectFirst("td.by > a[target]")?.text().orEmpty()
            val numText = tr.selectFirst("td.num em")?.text()
                ?: tr.selectFirst("td.num a.xi2")?.text()
                ?: ""
            val num = numText.filter { it.isDigit() }.toIntOrNull() ?: 0

            RankModel(
                title = title.trim(),
                url = url.trim(),
                authorName = author,
                authorUrl = authorUrl,
                date = date,
                index = index + 1,
                num = num,
                forum = forum,
                type = rankType
            )
        } catch (e: Exception) {
            Timber.w(e, "【热榜】解析单行失败")
            null
        }
    }.filterNotNull()

    val sorted = list.sortedByDescending { it.num }.mapIndexed { index, model ->
        model.copy(index = index + 1)
    }

    return RankListModel(sorted, time)
}
