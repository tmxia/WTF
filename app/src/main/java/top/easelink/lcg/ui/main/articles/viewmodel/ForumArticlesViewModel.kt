package top.easelink.lcg.ui.main.articles.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource.getForumArticles
import top.easelink.lcg.utils.WebsiteConstant.FORUM_URL_QUERY
import top.easelink.lcg.utils.showMessage

const val DEFAULT_ORDER = ""

class ForumArticlesViewModel : ViewModel(), ArticleFetcher {

    private var mUrl: String? = null
    private var mFetchType = ArticleFetcher.FetchType.FETCH_INIT
    private var orderType = DEFAULT_ORDER
    private var mCurrentPage = 1
    private var isTabSet = false
    private var fetching = false 

    val title = MutableLiveData<String>()
    val articles = MutableLiveData<List<Article>>()
    val threadList = MutableLiveData<List<ForumThread>>()
    val isLoading = MutableLiveData<Boolean>()

    fun initUrlAndFetch(
        url: String,
        fetchType: ArticleFetcher.FetchType,
        order: String = DEFAULT_ORDER
    ) {
        
        val baseUrl = if (url.contains("?")) url.substringBefore("?") else url
        val params = if (url.contains("?")) url.substringAfter("?") else ""
        
        
        val cleanParams = params.split("&").filter { it.isNotBlank() && !it.startsWith("page=") }
        val paramString = if (cleanParams.isNotEmpty()) cleanParams.joinToString("&") else ""

        mUrl = if (baseUrl.startsWith("forum-") && baseUrl.endsWith("html")) {
            try {
                val fid = baseUrl.split("-").getOrNull(1)
                if (fid != null) {
                    val formatted = String.format(FORUM_URL_QUERY, fid)
                    if (paramString.isNotEmpty()) "$formatted&$paramString" else formatted
                } else {
                    url
                }
            } catch (e: Exception) {
                Timber.e(e, "Forum URL parse failed")
                url
            }
        } else {
            if (paramString.isNotEmpty()) "$baseUrl?$paramString" else baseUrl
        }

        mFetchType = fetchType
        orderType = order
        rewindPageNum()
        fetchArticles(mFetchType) {}
    }

    @MainThread
    fun setTitle(t: String) {
        title.value = t
    }

    private fun composeUrlByRequestType(type: ArticleFetcher.FetchType): String {
        when (type) {
            ArticleFetcher.FetchType.FETCH_INIT -> rewindPageNum()
            ArticleFetcher.FetchType.FETCH_MORE -> nextPage()
        }
        
        val finalUrl = mUrl?.replace(Regex("&?page=\\d+"), "") ?: ""
        return "$finalUrl&page=$mCurrentPage$orderType"
    }

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        if (fetching) {
            callback(false)
            return
        }
        fetching = true
        isLoading.value = true
        
        viewModelScope.launch(IOPool) {
            try {
                val query = composeUrlByRequestType(fetchType)
                
                val forumPage = getForumArticles(query, fetchType == ArticleFetcher.FetchType.FETCH_INIT)
                
                if (forumPage == null || forumPage.articleList.isEmpty()) {
                    
                    if (fetchType == ArticleFetcher.FetchType.FETCH_MORE) {
                        showMessage(R.string.no_more_content)
                    }
                    callback(false)
                } else {
                    handleArticleList(fetchType, forumPage.articleList, callback)
                    
                    if (!isTabSet) {
                        threadList.postValue(forumPage.threadList.ifEmpty { emptyList() })
                        isTabSet = true
                    }
                }
            } catch (_: LoginRequiredException) {
                showMessage(R.string.login_required_error)
                callback(false)
            } catch (_: Exception) {
                showMessage(R.string.error)
                callback(false)
            } finally {
                fetching = false
                isLoading.postValue(false) 
            }
        }
    }

    private fun handleArticleList(
        fetchType: ArticleFetcher.FetchType,
        newList: List<Article>,
        callback: (Boolean) -> Unit
    ) {
        val currentList = articles.value
        
        when {
            
            fetchType != ArticleFetcher.FetchType.FETCH_MORE || currentList.isNullOrEmpty() -> {
                articles.postValue(newList)
                callback(true)
            }
            
            newList.lastOrNull()?.title == currentList.lastOrNull()?.title -> {
                showMessage(R.string.no_more_content)
                callback(false)
            }
            else -> {
                articles.postValue(currentList + newList)
                callback(true)
            }
        }
    }

    private fun rewindPageNum() {
        mCurrentPage = 1
    }

    private fun nextPage() {
        mCurrentPage++
    }
}
