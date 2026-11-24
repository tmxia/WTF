package top.easelink.lcg.ui.main.source.remote

import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.model.NotificationModel
import top.easelink.lcg.ui.main.source.parseNotificationModel

/**
 * 通知列表远程数据源：只负责请求编排 + 调用 MessageParser。
 */
object NotificationRemoteSource {

    suspend fun fetchNotifications(query: String): NotificationModel =
        parseNotificationModel(JsoupClient.sendGetRequestWithQuery(query))
}
