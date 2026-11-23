package top.easelink.lcg.ui.main.source

import androidx.annotation.WorkerThread
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.ui.main.model.BaseNotification
import top.easelink.lcg.ui.main.model.Conversation
import top.easelink.lcg.ui.main.model.NotificationModel
import top.easelink.lcg.ui.main.model.SystemNotification

/**
 * 消息域解析收口：纯 DOM -> model 映射，不持有 ViewModel 状态。
 * 站点改版时只需调整本文件的选择器，会话/通知 ViewModel 业务代码不动。
 */

/** 私信会话列表解析结果：formhash 供后续翻页提交使用，会话列表供 UI 渲染。 */
data class ConversationPage(
    val formHash: String?,
    val conversations: List<Conversation>
)

@WorkerThread
fun parseConversationPage(doc: Document): ConversationPage {
    val formHash = doc.selectFirst("input[name=formhash]")?.attr("value")
    val conversations = doc.select("dl[id^=pmlist]").map {
        val avatarUrl = it.selectFirst("img[onerror]")?.attr("src")
        val username = it.selectFirst("dd.ptm > a[target]")?.text().orEmpty()
        val lastMessage = it.selectFirst("dd.ptm")?.let { ddPtmElement ->
            ddPtmElement.textNodes().let { node ->
                if (node.size > 5) {
                    node[4].text().replaceFirst(" ", "")
                } else {
                    ""
                }
            }
        }.orEmpty()

        val datetime = it.selectFirst("span.xg1")?.text().orEmpty()
        val replyUrl = it.selectFirst("a[id^=pmlist]")?.attr("href")
        Conversation(
            avatar = avatarUrl,
            lastMessage = lastMessage,
            lastMessageDateTime = datetime,
            username = username,
            totalMessage = null,
            replyUrl = replyUrl
        )
    }
    return ConversationPage(formHash, conversations)
}

@WorkerThread
fun parseNotificationModel(doc: Document): NotificationModel {
    val notifications = doc.select("dl.cl").mapNotNull { element ->
        try {
            val ntc = element.selectFirst("dd.ntc_body")
            val avatarElement = element.selectFirst("img")
            val dateTimeElement = element.selectFirst("span.xg1")

            if (ntc == null || avatarElement == null || dateTimeElement == null) {
                return@mapNotNull null
            }

            ntc.getElementsByTag("a").forEach {
                val href = it.attr("href")
                if (href.isNotEmpty()) {
                    it.attr("href", "lcg:$href")
                }
            }
            BaseNotification(
                avatar = avatarElement.attr("src"),
                content = ntc.html(),
                dateTime = dateTimeElement.text()
            )
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }
    val nextPageUrl = doc.select("a.nxt").attr("href")
    return NotificationModel(notifications, nextPageUrl)
}

@WorkerThread
fun parseSystemNotifications(doc: Document): List<SystemNotification> {
    val dateTimeList = doc.select("span.xg1").map { it.text() }
    return doc.select("dl.cl").mapIndexed { index, element ->
        val title = element.selectFirst("dd.ntc_body")?.html().orEmpty()
        val dateTime = dateTimeList.getOrNull(index).orEmpty()
        SystemNotification(title = title, dateTime = dateTime)
    }
}
