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
    private var fetching = false // 核心防抖锁

    val title = MutableLiveData<String>()
    val articles = MutableLiveData<List<Article>>()
    val threadList = MutableLiveData<List<ForumThread>>()
    val isLoading = MutableLiveData<Boolean>()

    fun initUrlAndFetch(
        url: String,
        fetchType: ArticleFetcher.FetchType,
        order: String = DEFAULT_ORDER
    ) {
        // 核心修复：完美处理带 typeid 等过滤参数的 URL
        val baseUrl = if (url.contains("?")) url.substringBefore("?") else url
        val params = if (url.contains("?")) url.substringAfter("?") else ""
        
        // 清理可能残留的旧 page 参数，防止双重 page 导致无限重试
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
        // 核心修复：如果 URL 里已经有 page 参数（极少数情况），先删掉它再拼，防止死循环
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
                // 核心修复：安全调用，防止 null 导致转圈不停止
                val forumPage = getForumArticles(query, fetchType == ArticleFetcher.FetchType.FETCH_INIT)
                
                if (forumPage == null || forumPage.articleList.isEmpty()) {
                    // 如果返回空，立刻结束加载，不发后续请求
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
                isLoading.postValue(false) // 核心：必定结束 Loading
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
            // 首次加载或列表本身为空
            fetchType != ArticleFetcher.FetchType.FETCH_MORE || currentList.isNullOrEmpty() -> {
                articles.postValue(newList)
                callback(true)
            }
            // 防重复：新列表的最后一条和旧列表的最后一条一样，说明到底了
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
