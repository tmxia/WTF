package top.easelink.lcg.ui.main.article.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.source.parseReplyForm
import top.easelink.lcg.utils.WebsiteConstant.CHECK_RULE_URL
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

class ReplyPostViewModel : ViewModel() {
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun sendReply(query: String?, content: String, callback: (Boolean) -> Unit) {
        _sending.value = true
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { sendReplyAsync(query, content) }
            }.onFailure { Timber.e(it) }.getOrDefault(false)
            callback(ok)
            _sending.value = false
        }
    }

    private suspend fun sendReplyAsync(query: String?, content: String): Boolean {
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
