package top.easelink.lcg.ui.main.message.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document
import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.model.Conversation
import top.easelink.lcg.utils.WebsiteConstant

class ConversationListViewModel : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 分页骨架：当前为单页解析，暂未提取下一页链接，hasMore 恒为 false。 */
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var page = 1
    private var formHash: String? = null

    fun fetchConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                parseConversations(JsoupClient.sendGetRequestWithQuery(WebsiteConstant.PRIVATE_MESSAGE_QUERY))
            } catch (e: Exception) {
                Timber.e(e)
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        // TODO 会话分页：解析下一页链接后按 page+1 请求；当前无翻页数据源，直接返回。
        if (_hasMore.value != true) return
    }

    private fun parseConversations(doc: Document) {
        doc.apply {
            formHash = selectFirst("input[name=formhash]")?.attr("value")
            val conversationList = select("dl[id^=pmlist]").map {
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
            _conversations.value = conversationList
        }
    }
}
