package top.easelink.lcg.ui.main.source.remote

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.follow.model.FollowResult
import top.easelink.lcg.ui.main.source.parseFollowResult

/**
 * 关注 / 粉丝列表远程数据源：只负责请求编排 + 调用 FollowParser。
 */
object FollowListRemoteSource {

    suspend fun fetchFollowList(url: String): FollowResult =
        parseFollowResult(JsoupClient.sendGetRequestWithQuery(url))
}
