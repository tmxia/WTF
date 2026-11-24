package top.easelink.lcg.ui.main.source.remote

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.parseReplyForm
import top.easelink.lcg.utils.WebsiteConstant.CHECK_RULE_URL
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

/**
 * 回复提交远程数据源：表单获取、规则校验、发帖请求编排收口于此；ViewModel 只负责状态调度。
 */
object ReplyPostRemoteSource {

    suspend fun sendReply(query: String?, content: String): Boolean {
        Timber.d(content)
        if (query.isNullOrEmpty() || content.isBlank()) return false

        val queryMap = query.split("&")
            .mapNotNull { entry ->
                val pair = entry.split("=", limit = 2)
                if (pair[0].isNotBlank()) pair[0] to (pair.getOrNull(1) ?: "") else null
            }
            .toMap()

        val formDoc = JsoupClient.sendGetRequestWithQuery(query)
        val fields = parseReplyForm(formDoc)

        JsoupClient.sendGetRequestWithUrl(CHECK_RULE_URL)

        val replyUrl = "${SERVER_BASE_URL}forum.php?mod=post&infloat=yes&action=reply" +
            "&fid=${queryMap["fid"]}&extra=${queryMap["extra"]}&tid=${queryMap["tid"]}" +
            "&replysubmit=yes&inajax=1"

        JsoupClient.sendPostRequestWithUrl(
            replyUrl,
            mutableMapOf(
                "formhash" to fields.formHash,
                "handlekey" to fields.handlekey,
                "noticeauthor" to fields.noticeauthor,
                "noticetrimstr" to fields.noticetrimstr,
                "noticeauthormsg" to fields.noticeauthormsg,
                "usesig" to fields.usesig,
                "reppid" to fields.reppid,
                "reppost" to fields.reppost,
                "subject" to "",
                "message" to content
            )
        )
        return true // [ARCH-PHASE-10] OkHttp 底层已接管状态码
    }
}
