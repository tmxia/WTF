package top.easelink.lcg.ui.main.message.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.model.Conversation
import top.easelink.lcg.ui.main.source.parseConversationPage
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.WebsiteConstant

class ConversationListViewModel : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<Conversation>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<Conversation>>> = _uiState.asStateFlow()

    
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var page = 1
    private var formHash: String? = null

    fun fetchConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _uiState.value = PageUiState.Loading
            try {
                val page = parseConversationPage(
                    JsoupClient.sendGetRequestWithQuery(WebsiteConstant.PRIVATE_MESSAGE_QUERY)
                )
                formHash = page.formHash
                _conversations.value = page.conversations
                if (_conversations.value.isNotEmpty()) {
                    _uiState.value = PageUiState.Success(_conversations.value)
                } else {
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        // TODO 会话分页：解析下一页链接后按 page+1 请求；当前无翻页数据源，直接返回。
        if (_hasMore.value != true) return
    }
}
