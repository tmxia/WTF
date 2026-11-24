package top.easelink.lcg.ui.main.follow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.follow.model.FeedInfo
import top.easelink.lcg.ui.main.source.remote.FollowingFeedRemoteSource
import top.easelink.lcg.utils.ErrorHandler

class FollowingFeedViewModel : ViewModel() {
    private val _follows = MutableStateFlow<List<FeedInfo>>(emptyList())
    val follows: StateFlow<List<FeedInfo>> = _follows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<FeedInfo>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<FeedInfo>>> = _uiState.asStateFlow()

    private val _isLoadingForLoadMore = MutableStateFlow(false)
    val isLoadingForLoadMore: StateFlow<Boolean> = _isLoadingForLoadMore.asStateFlow()

    var pageNum = 1

    fun fetchData() {
        _isLoading.value = true
        _uiState.value = PageUiState.Loading
        viewModelScope.launch(IOPool) {
            try {
                val page = FollowingFeedRemoteSource.fetchFeed(1)
                _follows.value = page.feeds
                if (_follows.value.isNotEmpty()) {
                    _uiState.value = PageUiState.Success(_follows.value)
                } else {
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchMore(callBack: (Boolean) -> Unit) {
        _isLoadingForLoadMore.value = true
        viewModelScope.launch(IOPool) {
            try {
                val page = FollowingFeedRemoteSource.fetchFeed(pageNum)
                _follows.value = page.feeds
                callBack(page.valid)
                if (page.valid) {
                    pageNum += 1
                }
            } catch (e: Exception) {
                Timber.e(e)
                callBack(false)
            } finally {
                _isLoadingForLoadMore.value = false
            }
        }
    }
}
