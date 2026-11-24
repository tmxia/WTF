package top.easelink.lcg.ui.main.source.remote

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.ConversationPage
import top.easelink.lcg.ui.main.source.parseConversationPage
import top.easelink.lcg.utils.WebsiteConstant.PRIVATE_MESSAGE_QUERY

/**
 * 私信会话列表远程数据源：只负责请求编排 + 调用 MessageParser。
 */
object ConversationRemoteSource {

    suspend fun fetchConversations(): ConversationPage =
        parseConversationPage(JsoupClient.sendGetRequestWithQuery(PRIVATE_MESSAGE_QUERY))
}
