package top.easelink.lcg.ui.search.viewmodel

import android.content.Context
import top.easelink.lcg.appinit.LCGApp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import top.easelink.lcg.ui.main.discover.source.ForumNetworkSource
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchNextPage
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchWith

class LCGSearchViewModel : ViewModel(), LCGSearchResultAdapter.ContentFetcher {
    val searchResults = MutableLiveData<List<LCGSearchResultItem>>()
    val totalResult = MutableLiveData<String?>()
    val isLoading = MutableLiveData<Boolean>()
    val isLoadingMore = MutableLiveData<Boolean>()
    
        private val prefs = LCGApp.context.getSharedPreferences("wtf_search_prefs", Context.MODE_PRIVATE)

    val selectedFidName = MutableLiveData<String>(prefs.getString("search_fid_name", "全部板块") ?: "全部板块")
    val selectedSortName = MutableLiveData<String>(prefs.getString("search_sort_name", "按相关度") ?: "按相关度")
    val isMultiSelect = MutableLiveData<Boolean>(false)
    private var mFids: List<String> = prefs.getString("search_fids", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    private var mSortOrder: String? = prefs.getString("search_sort_order", null)
    
    
    val currentFids: List<String> get() = mFids
    val forumList = MutableLiveData<List<ForumNavigationModel>>()
    
    override fun fetch(type: LCGSearchResultAdapter.ContentFetcher.Type, callback: ((Boolean) -> Unit)?) {
        
    }

    private var mKeyWord: String? = null
    private var searchJob: Job? = null
    private var hasMore = true

    fun loadForums() {
        if (!forumList.value.isNullOrEmpty()) return
        GlobalScope.launch(IOPool) {
            forumList.postValue(ForumNetworkSource.fetchAllForumsFromApi())
        }
    }

    fun selectSort(orderby: String?, name: String) {
        mSortOrder = orderby
        selectedSortName.value = name
        prefs.edit().putString("search_sort_order", orderby)
            .putString("search_sort_name", name).apply()
        if (!mKeyWord.isNullOrBlank()) {
            fetchInit(mKeyWord!!)
        }
    }

    fun selectFids(fids: List<String>, name: String) {
        mFids = fids
        selectedFidName.value = name
        isMultiSelect.value = fids.size > 1
        prefs.edit().putString("search_fids", fids.joinToString(","))
            .putString("search_fid_name", name).apply()
        if (!mKeyWord.isNullOrBlank()) {
            fetchInit(mKeyWord!!)
        }
    }

    fun setKeyword(keyword: String?) {
        mKeyWord = keyword
        fetchInit(keyword)
    }

    private fun fetchInit(keyword: String?) {
        if (keyword.isNullOrBlank()) return
        isLoading.value = true
        hasMore = true
        
        // 核心修复：取消上一次未完成的搜索，防止结果覆盖
        searchJob?.cancel()
        searchJob = viewModelScope.launch(IOPool) {
            val result = doSearchWith(keyword, mFids.joinToString(","), mSortOrder)
            if (result.searchResultList.isNotEmpty() || result.totalResult != null) {
                searchResults.postValue(result.searchResultList)
                totalResult.postValue(result.totalResult)
                hasMore = result.searchResultList.isNotEmpty()
            } else {
                // 如果返回空，说明可能被限流或 WAF 拦截，保持原有列表或清空（此处选择清空）
                searchResults.postValue(emptyList())
                totalResult.postValue(result.totalResult)
            }
            isLoading.postValue(false)
        }
    }

    fun loadNextPage() {
        if (isLoadingMore.value == true || !hasMore) return
        isLoadingMore.value = true
        GlobalScope.launch(IOPool) {
            doSearchNextPage().let {
                val currentList = searchResults.value?.toMutableList() ?: mutableListOf()
                if (it.searchResultList.isEmpty()) {
                    hasMore = false
                } else {
                    currentList.addAll(it.searchResultList)
                    searchResults.postValue(currentList)
                }
                if (!it.totalResult.isNullOrEmpty()) totalResult.postValue(it.totalResult)
            }
            isLoadingMore.postValue(false)
        }
    }
}
