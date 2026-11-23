package top.easelink.lcg.ui.search.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import top.easelink.lcg.ui.main.discover.source.ForumNetworkSource
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchNextPage
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchWith
import top.easelink.lcg.utils.ErrorHandler

class LCGSearchViewModel : ViewModel(), LCGSearchResultAdapter.ContentFetcher {
    var lastSearchKeyword: String = ""
    var hasSearchedOnce: Boolean = false

    private val _searchResults = MutableStateFlow<List<LCGSearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<LCGSearchResultItem>> = _searchResults.asStateFlow()

    private val _totalResult = MutableStateFlow<String?>(null)
    val totalResult: StateFlow<String?> = _totalResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<LCGSearchResultItem>>>(PageUiState.Empty)
    val uiState: StateFlow<PageUiState<List<LCGSearchResultItem>>> = _uiState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val prefs = try {
        LCGApp.context.getSharedPreferences("wtf_search_prefs", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        Timber.tag("[SearchProbe]").e(e, "获取 SharedPreferences 崩溃，Context 异常！")
        throw e
    }

    private val _selectedFidName = MutableStateFlow(prefs.getString("search_fid_name", "全部板块") ?: "全部板块")
    val selectedFidName: StateFlow<String> = _selectedFidName.asStateFlow()

    private val _selectedSortName = MutableStateFlow(prefs.getString("search_sort_name", "按相关度") ?: "按相关度")
    val selectedSortName: StateFlow<String> = _selectedSortName.asStateFlow()

    private val _isMultiSelect = MutableStateFlow(false)
    val isMultiSelect: StateFlow<Boolean> = _isMultiSelect.asStateFlow()

    private var mFids: List<String> = prefs.getString("search_fids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    private var mSortOrder: String? = prefs.getString("search_sort_order", null)

    val currentFids: List<String> get() = mFids

    private val _forumList = MutableStateFlow<List<ForumNavigationModel>>(emptyList())
    val forumList: StateFlow<List<ForumNavigationModel>> = _forumList.asStateFlow()

    override fun fetch(type: LCGSearchResultAdapter.ContentFetcher.Type, callback: ((Boolean) -> Unit)?) {
    }

    private var mKeyWord: String? = null
    private var searchJob: Job? = null
    private var hasMore = true

    fun loadForums() {
        if (_forumList.value.isNotEmpty()) return
        viewModelScope.launch(IOPool) {
            _forumList.value = ForumNetworkSource.fetchAllForumsFromApi()
        }
    }

    fun selectSort(orderby: String?, name: String) {
        mSortOrder = orderby
        _selectedSortName.value = name
        prefs.edit().putString("search_sort_order", orderby)
            .putString("search_sort_name", name).apply()
        val keyword = mKeyWord
        if (!keyword.isNullOrBlank()) {
            fetchInit(keyword)
        }
    }

    fun selectFids(fids: List<String>, name: String) {
        mFids = fids
        _selectedFidName.value = name
        _isMultiSelect.value = fids.size > 1
        prefs.edit().putString("search_fids", fids.joinToString(","))
            .putString("search_fid_name", name).apply()
        val keyword = mKeyWord
        if (!keyword.isNullOrBlank()) {
            fetchInit(keyword)
        }
    }

    fun setKeyword(keyword: String?) {
        mKeyWord = keyword
        fetchInit(keyword)
    }

    private fun fetchInit(keyword: String?) {
        Timber.tag("[SearchProbe]").d("发起搜索请求: keyword=$keyword, fids=$mFids, sort=$mSortOrder")
        if (keyword.isNullOrBlank()) return
        _isLoading.value = true
        _uiState.value = PageUiState.Loading
        hasMore = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch(IOPool) {
            try {
                val result = doSearchWith(keyword, mFids.joinToString(","), mSortOrder)
                if (result != null) {
                    Timber.tag("[SearchProbe]").d("收到搜索结果: 数量=${result.searchResultList.size}")
                    _searchResults.value = result.searchResultList
                    _totalResult.value = result.totalResult
                    hasMore = result.searchResultList.isNotEmpty()
                    _uiState.value = if (result.searchResultList.isNotEmpty()) {
                        PageUiState.Success(result.searchResultList)
                    } else {
                        PageUiState.Empty
                    }
                } else {
                    Timber.tag("SearchDebug").d("搜索被限流，忽略此次请求")
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.tag("SearchDebug").e(e, "搜索异常：${e.message}")
                withContext(Dispatchers.Main) {
                    val msg = e.message
                    if (msg == "WAF_VERIFICATION_REQUIRED") {
                        top.easelink.lcg.utils.showMessage("触发安全风控，请完成验证")
                    } else if (!msg.isNullOrBlank()) {
                        top.easelink.lcg.utils.showMessage(msg)
                    } else {
                        top.easelink.lcg.utils.showMessage(top.easelink.lcg.R.string.network_error)
                    }
                }
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        val keyword = mKeyWord
        if (!keyword.isNullOrBlank()) {
            fetchInit(keyword)
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !hasMore) return

        _isLoadingMore.value = true
        viewModelScope.launch(IOPool) {
            try {
                val result = doSearchNextPage()
                if (result != null) {
                    val currentList = _searchResults.value.toMutableList()
                    if (result.searchResultList.isEmpty()) {
                        hasMore = false
                    } else {
                        currentList.addAll(result.searchResultList)
                        _searchResults.value = currentList
                    }
                    if (!result.totalResult.isNullOrEmpty()) _totalResult.value = result.totalResult
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                Timber.tag("SearchDebug").e(e, "搜索异常：${e.message}")
                withContext(Dispatchers.Main) {
                    val msg = e.message
                    if (msg == "WAF_VERIFICATION_REQUIRED") {
                        top.easelink.lcg.utils.showMessage("触发安全风控，请完成验证")
                    } else if (!msg.isNullOrBlank()) {
                        top.easelink.lcg.utils.showMessage(msg)
                    } else {
                        top.easelink.lcg.utils.showMessage(top.easelink.lcg.R.string.network_error)
                    }
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
