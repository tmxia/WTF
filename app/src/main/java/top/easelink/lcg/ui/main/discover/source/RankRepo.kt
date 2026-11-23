package top.easelink.lcg.ui.main.discover.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.lcg.cache.HotTopicCacheManager
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.discover.model.RankListModel
import top.easelink.lcg.ui.main.source.parseRankModelInfo
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
