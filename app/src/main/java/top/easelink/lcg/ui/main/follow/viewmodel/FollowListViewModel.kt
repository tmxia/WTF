package top.easelink.lcg.ui.main.follow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.follow.model.FollowResult
import top.easelink.lcg.ui.main.source.parseFollowResult

class FollowListViewModel : ViewModel() {
    private val _follows = MutableStateFlow<FollowResult?>(null)
    val follows: StateFlow<FollowResult?> = _follows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingForLoadMore = MutableStateFlow(false)
    val isLoadingForLoadMore: StateFlow<Boolean> = _isLoadingForLoadMore.asStateFlow()

    fun fetchData(url: String, isLoadMore: Boolean = false) {
        if (isLoadMore) {
            _isLoadingForLoadMore.value = true
        } else {
            _isLoading.value = true
        }

        viewModelScope.launch(IOPool) {
            try {
                val document = JsoupClient.sendGetRequestWithQuery(url)
                _follows.value = parseFollowResult(document)
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                if (isLoadMore) {
                    _isLoadingForLoadMore.value = false
                } else {
                    _isLoading.value = false
                }
            }
        }
    }
}
