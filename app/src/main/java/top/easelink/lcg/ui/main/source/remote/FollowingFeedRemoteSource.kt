package top.easelink.lcg.ui.main.source.remote

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.FeedPage
import top.easelink.lcg.ui.main.source.parseFeedPage
import top.easelink.lcg.utils.WebsiteConstant.FOLLOW_FEED_QUERY
import java.util.Locale

/**
 * 关注动态流远程数据源：只负责请求编排 + 调用 FollowParser，ViewModel 不直接触达网络层。
 */
object FollowingFeedRemoteSource {

    suspend fun fetchFeed(pageNum: Int): FeedPage {
        val url = String.format(Locale.US, FOLLOW_FEED_QUERY, pageNum, 1)
        return parseFeedPage(JsoupClient.sendAjaxRequest(url))
    }
}
