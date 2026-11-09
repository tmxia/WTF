package top.easelink.lcg.ui.search.viewmodel

import android.content.Context
import timber.log.Timber
import top.easelink.lcg.appinit.LCGApp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import top.easelink.lcg.ui.main.discover.source.ForumNetworkSource
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchNextPage
import top.easelink.lcg.ui.search.source.LCGSearchService.doSearchWith

class LCGSearchViewModel : ViewModel(), LCGSearchResultAdapter.ContentFetcher {
    var lastSearchKeyword: String = ""
    var hasSearchedOnce: Boolean = false
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
            try {
                val result = doSearchWith(keyword, mFids.joinToString(","), mSortOrder)
                if (result != null) {
                    // 只有真实拿到结果（哪怕空列表），才刷新 UI
                    searchResults.postValue(result.searchResultList)
                    totalResult.postValue(result.totalResult)
                    hasMore = result.searchResultList.isNotEmpty()
                } else {
                    // 返回 null 说明被限流了（点击太快），不做任何 UI 更新
                    Timber.tag("SearchDebug").d("搜索被限流，忽略此次请求")
                }
            } catch (e: Exception) {
                // 核心修复：如果遇到 WAF 或其他网络错误，不清空原有列表，只弹提示
                Timber.tag("SearchDebug").e(e, "搜索异常：${e.message}")
                if (e.message == "WAF_VERIFICATION_REQUIRED") {
                    withContext(Dispatchers.Main) {
                        top.easelink.lcg.utils.showMessage("触发安全风控，请稍后再试")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        top.easelink.lcg.utils.showMessage(top.easelink.lcg.R.string.network_error)
                    }
                }
                // 注意：这里千万不能写 searchResults.postValue(emptyList())，否则把用户的列表清空了
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun loadNextPage() {
        // 防抖：如果已经在加载，或者没有更多了，直接返回
        if (isLoadingMore.value == true || !hasMore) return
        
        isLoadingMore.value = true
        viewModelScope.launch(IOPool) {
            try {
                val result = doSearchNextPage()
                if (result != null) {
                    val currentList = searchResults.value?.toMutableList() ?: mutableListOf()
                    if (result.searchResultList.isEmpty()) {
                        hasMore = false
                    } else {
                        currentList.addAll(result.searchResultList)
                        searchResults.postValue(currentList)
                    }
                    if (!result.totalResult.isNullOrEmpty()) totalResult.postValue(result.totalResult)
                } else {
                    hasMore = false // 返回 null 代表没有下一页了
                }
            } catch (e: Exception) {
                Timber.tag("SearchDebug").e(e, "加载下一页失败")
                // 失败时不改变 hasMore 状态，允许用户重试
            } finally {
                // 无论如何，都要把加载状态重置，否则底部会一直转圈
                isLoadingMore.postValue(false)
            }
        }
    }
}
