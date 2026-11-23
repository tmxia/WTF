package top.easelink.lcg.ui.main.discover.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.cache.HotTopicCacheManager
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.RankListModel
import top.easelink.lcg.ui.main.discover.model.RankModel
import top.easelink.lcg.utils.WebsiteConstant.RANK_HOT_QUERY


private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

suspend fun fetchRank(rankType: RankType, dateType: DateType): RankListModel {
    var doc = HotTopicCacheManager.findTodayHotTopic(rankType = rankType.value, dateType = dateType.value)
    var model = doc?.let { parseRankModelInfo(it, rankType) }

    
    if (model == null || model.listModel.isEmpty()) {
        doc = JsoupClient.sendGetRequestWithQuery(RANK_HOT_QUERY)
        model = parseRankModelInfo(doc, rankType)
        if (model.listModel.isNotEmpty()) {
            backgroundScope.launch {
                HotTopicCacheManager.saveToDisk(
                    rankType = rankType.value,
                    dateType = dateType.value,
                    timeStamp = System.currentTimeMillis(),
                    content = doc.html()
                )
            }
        } else {
            Timber.w("【热榜】guide&view=hot 解析为空，未写入缓存；页面可能被风控或结构已变更")
        }
    }
    return model
}

fun parseRankModelInfo(document: Document, rankType: RankType): RankListModel = with(document) {
    val time = selectFirst("div.notice")?.text().orEmpty()

    
    val rows = buildList {
        addAll(select("tbody[id^=normal]"))
        if (isEmpty()) addAll(select("tbody[id^=normalthread]"))
        if (isEmpty()) addAll(select("table tbody tr"))
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

    RankListModel(sorted, time)
}

enum class DateType(val value: String) {
    TODAY("today"),
    WEEK("thisweek")
}

enum class RankType(val value: String) {
    VIEW("views"),
    REPLY("replies"),
    HEAT("heat"),
    FAVORITE("favtimes"),
    SHARE("sharetimes")
}
