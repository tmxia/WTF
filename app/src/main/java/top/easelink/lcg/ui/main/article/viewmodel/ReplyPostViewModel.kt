package top.easelink.lcg.ui.main.article.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.lcg.ui.main.source.remote.ReplyPostRemoteSource

class ReplyPostViewModel : ViewModel() {
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun sendReply(query: String?, content: String, callback: (Boolean) -> Unit) {
        _sending.value = true
        viewModelScope.launch {
            val ok = runCatching {
                ReplyPostRemoteSource.sendReply(query, content)
            }.onFailure { Timber.e(it) }.getOrDefault(false)
            callback(ok)
            _sending.value = false
        }
    }
}
