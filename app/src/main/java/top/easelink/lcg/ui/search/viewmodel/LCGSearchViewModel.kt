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
    
        private val prefs = try {
        LCGApp.context.getSharedPreferences("wtf_search_prefs", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        Timber.tag("[SearchProbe]").e(e, "获取 SharedPreferences 崩溃，Context 异常！")
        throw e
    }

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
        Timber.tag("[SearchProbe]").d("发起搜索请求: keyword=$keyword, fids=$mFids, sort=$mSortOrder")
        if (keyword.isNullOrBlank()) return
        isLoading.value = true
        hasMore = true
        
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch(IOPool) {
            try {
                val result = doSearchWith(keyword, mFids.joinToString(","), mSortOrder)
                if (result != null) {
                    
                    Timber.tag("[SearchProbe]").d("收到搜索结果: 数量=${result.searchResultList.size}")
                    searchResults.postValue(result.searchResultList)
                    totalResult.postValue(result.totalResult)
                    hasMore = result.searchResultList.isNotEmpty()
                } else {
                    
                    Timber.tag("SearchDebug").d("搜索被限流，忽略此次请求")
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
                isLoading.postValue(false)
            }
        }
    }

    fun loadNextPage() {
        
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
                
                isLoadingMore.postValue(false)
            }
        }
    }
}
