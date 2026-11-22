package top.easelink.lcg.ui.main.articles.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.appinit.LCGApp
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource.getForumArticles
import top.easelink.lcg.utils.WebsiteConstant.FORUM_URL_QUERY
import top.easelink.lcg.utils.showMessage

const val DEFAULT_ORDER = ""

// 板块列表排序白名单（forumdisplay 支持的 orderby 取值）
private val allowedOrderby = setOf("dateline", "lastpost")

class ForumArticlesViewModel : ViewModel(), ArticleFetcher {
    private var mUrl: String? = null
    private var mFetchType = ArticleFetcher.FetchType.FETCH_INIT
    private var mCurrentPage = 1
    private var isTabSet = false
    private var fetching = false

    private val prefs = try {
        LCGApp.context.getSharedPreferences("wtf_forum_prefs", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        Timber.tag("[ForumSort]").e(e, "获取 SharedPreferences 崩溃")
        throw e
    }

    private var orderType = restoreOrderType()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _selectedSortName = MutableStateFlow(
        prefs.getString("forum_sort_name", "默认顺序") ?: "默认顺序"
    )
    val selectedSortName: StateFlow<String> = _selectedSortName.asStateFlow()

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _threadList = MutableStateFlow<List<ForumThread>>(emptyList())
    val threadList: StateFlow<List<ForumThread>> = _threadList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun initUrlAndFetch(
        url: String,
        fetchType: ArticleFetcher.FetchType,
        order: String = DEFAULT_ORDER
    ) {
        val baseUrl = if (url.contains("?")) url.substringBefore("?") else url
        val params = if (url.contains("?")) url.substringAfter("?") else ""

        val cleanParams = params.split("&").filter {
            it.isNotBlank() &&
                !it.startsWith("page=") &&
                !it.startsWith("orderby=") &&
                !it.startsWith("ascdesc=")
        }
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
        if (order != DEFAULT_ORDER) orderType = order
        rewindPageNum()
        fetchArticles(mFetchType) {}
    }

    private fun restoreOrderType(): String {
        val orderby = prefs.getString("forum_sort_order", null)
        return if (orderby in allowedOrderby) "&orderby=$orderby" else DEFAULT_ORDER
    }

    /**
     * 切换板块列表排序。白名单校验后持久化选择，重置页码并从第 1 页重新加载。
     * orderby 为 null 表示恢复系统默认顺序。
     */
    fun selectSort(orderby: String?, name: String) {
        val safe = orderby?.takeIf { it in allowedOrderby }
        orderType = if (safe != null) "&orderby=$safe" else DEFAULT_ORDER
        _selectedSortName.value = name
        prefs.edit()
            .putString("forum_sort_order", safe)
            .putString("forum_sort_name", name)
            .apply()
        mFetchType = ArticleFetcher.FetchType.FETCH_INIT
        rewindPageNum()
        fetchArticles(ArticleFetcher.FetchType.FETCH_INIT) {}
    }

    @MainThread
    fun setTitle(t: String) {
        _title.value = t
    }

    private fun composeUrlByRequestType(type: ArticleFetcher.FetchType): String {
        when (type) {
            ArticleFetcher.FetchType.FETCH_INIT -> rewindPageNum()
            ArticleFetcher.FetchType.FETCH_MORE -> nextPage()
        }

        val finalUrl = mUrl
            ?.replace(Regex("&?page=\\d+"), "")
            ?.replace(Regex("&?(orderby|ascdesc)=[^&]*"), "")
            ?.trim('&')
            ?: ""
        return "$finalUrl&page=$mCurrentPage$orderType"
    }

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        if (fetching) {
            callback(false)
            return
        }
        fetching = true
        _isLoading.value = true

        viewModelScope.launch(IOPool) {
            try {
                val query = composeUrlByRequestType(fetchType)
                Timber.tag("[ForumSort]").d("请求列表: $query, orderType=$orderType")

                var forumPage = getForumArticles(query, fetchType == ArticleFetcher.FetchType.FETCH_INIT)
                var fellBackToDefault = false

                // 首拉且带排序时若返回空，自动降级回系统默认顺序重试一次，避免页面空白卡死
                if ((forumPage == null || forumPage.articleList.isEmpty()) &&
                    fetchType == ArticleFetcher.FetchType.FETCH_INIT &&
                    orderType != DEFAULT_ORDER
                ) {
                    val savedOrder = orderType
                    orderType = DEFAULT_ORDER
                    val fallbackQuery = composeUrlByRequestType(ArticleFetcher.FetchType.FETCH_INIT)
                    Timber.tag("[ForumSort]").w("排序结果为空，降级默认顺序重试: $fallbackQuery")
                    forumPage = getForumArticles(fallbackQuery, true)
                    fellBackToDefault = forumPage != null && forumPage.articleList.isNotEmpty()
                    if (!fellBackToDefault) {
                        orderType = savedOrder
                    }
                }

                if (forumPage == null || forumPage.articleList.isEmpty()) {
                    Timber.tag("[ForumSort]").w("列表为空, query=$query")
                    if (fetchType == ArticleFetcher.FetchType.FETCH_MORE) {
                        showMessage(R.string.no_more_content)
                    }
                    callback(false)
                } else {
                    if (fellBackToDefault) {
                        showMessage(R.string.sort_fallback_tip)
                    }
                    handleArticleList(fetchType, forumPage.articleList, callback)

                    if (!isTabSet) {
                        _threadList.value = forumPage.threadList.ifEmpty { emptyList() }
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
                _isLoading.value = false
            }
        }
    }

    private fun handleArticleList(
        fetchType: ArticleFetcher.FetchType,
        newList: List<Article>,
        callback: (Boolean) -> Unit
    ) {
        val currentList = _articles.value

        when {
            fetchType != ArticleFetcher.FetchType.FETCH_MORE || currentList.isNullOrEmpty() -> {
                _articles.value = newList
                callback(true)
            }

            newList.lastOrNull()?.title == currentList.lastOrNull()?.title -> {
                showMessage(R.string.no_more_content)
                callback(false)
            }

            else -> {
                _articles.value = currentList + newList
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
