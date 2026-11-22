package top.easelink.lcg.ui.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.ui.search.model.BaiduSearchResult
import top.easelink.lcg.ui.search.source.BaiduSearchService.doSearchRequest
import top.easelink.lcg.ui.search.viewmodel.BaiduSearchResultAdapter.SearchAdapterListener
import top.easelink.lcg.utils.WebsiteConstant
import top.easelink.lcg.utils.showMessage
import timber.log.Timber

class BaiduSearchViewModel : ViewModel(), SearchAdapterListener {
    private val _searchResults = MutableStateFlow<List<BaiduSearchResult>>(emptyList())
    val searchResults: StateFlow<List<BaiduSearchResult>> = _searchResults.asStateFlow()

    private val _mTotalResult = MutableStateFlow<String?>(null)
    val mTotalResult: StateFlow<String?> = _mTotalResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var mUrl: String? = null
    private var mNextPageUrl: String? = null

    fun initUrl(url: String?) {
        mUrl = url
        doSearchQuery(SearchAdapterListener.FETCH_INIT)
    }

    override fun doSearchQuery(type: Int) {
        _isLoading.value = true
        val requestUrl: String? = when {
            type == SearchAdapterListener.FETCH_MORE && !mNextPageUrl.isNullOrBlank() -> {
                WebsiteConstant.BING_SEARCH_BASE_URL + mNextPageUrl.orEmpty()
            }

            type == SearchAdapterListener.FETCH_INIT -> {
                mUrl
            }

            else -> {
                null
            }
        }
        if (requestUrl.isNullOrBlank()) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch(IOPool) {
            try {
                doSearchRequest(requestUrl, 0).apply {
                    if (baiduSearchResultList.isNotEmpty()) {
                        val list = _searchResults.value
                        mNextPageUrl = nextPageUrl
                        _mTotalResult.value = totalResult
                        if (type == SearchAdapterListener.FETCH_MORE && list.isNotEmpty()) {
                            _searchResults.value = list.plus(baiduSearchResultList)
                        } else {
                            _searchResults.value = baiduSearchResultList
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "baidu search failed")
                showMessage(R.string.network_error)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
