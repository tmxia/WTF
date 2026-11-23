package top.easelink.lcg.ui.main.articles.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread
import top.easelink.lcg.ui.main.articles.source.ForumQueryRules
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource.getForumArticles
import top.easelink.lcg.utils.WebsiteConstant.FORUM_URL_QUERY
import top.easelink.lcg.utils.showMessage
import top.easelink.lcg.utils.ErrorHandler

const val DEFAULT_ORDER = ""


class ForumArticlesViewModel : ViewModel(), ArticleFetcher {
    private var mUrl: String? = null
    private var mFetchType = ArticleFetcher.FetchType.FETCH_INIT
    private var mCurrentPage = 1
    private var isTabSet = false
    private var fetching = false

    
    private var refreshJob: Job? = null

    
    
    
    private var orderType = DEFAULT_ORDER
    private var datelineDays: Int? = null

    
    private var loadedForumUrl: String? = null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val prefs = top.easelink.lcg.utils.SharedPreferencesHelper.getUserSp()
    private val KEY_SORT_NAME = "forum_sort_name"
    private val KEY_TIME_DAYS = "forum_time_days"

    private val _selectedSortName = MutableStateFlow(prefs.getString(KEY_SORT_NAME, "默认顺序") ?: "默认顺序")
    val selectedSortName: StateFlow<String> = _selectedSortName.asStateFlow()

    private val _selectedTimeName = MutableStateFlow(
        if (_selectedSortName.value == "默认顺序") "不限时间" else getTimeName(prefs.getInt(KEY_TIME_DAYS, 7))
    )
    val selectedTimeName: StateFlow<String> = _selectedTimeName.asStateFlow()

    private val _timeLocked = MutableStateFlow(_selectedSortName.value == "默认顺序")
    val timeLocked: StateFlow<Boolean> = _timeLocked.asStateFlow()

    init {
        if (_selectedSortName.value != "默认顺序") {
            orderType = if (_selectedSortName.value == "按发帖时间") "&orderby=dateline" else "&orderby=lastpost"
            datelineDays = prefs.getInt(KEY_TIME_DAYS, 7)
        }
    }

    private fun getTimeName(days: Int): String {
        return when (days) {
            1 -> "一天"
            2 -> "两天"
            7 -> "一周"
            30 -> "一个月"
            90 -> "三个月"
            365 -> "一年"
            else -> "一周"
        }
    }

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _threadList = MutableStateFlow<List<ForumThread>>(emptyList())
    val threadList: StateFlow<List<ForumThread>> = _threadList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<Article>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<Article>>> = _uiState.asStateFlow()

    fun initUrlAndFetch(
        url: String,
        fetchType: ArticleFetcher.FetchType
    ) {
        val baseUrl = if (url.contains("?")) url.substringBefore("?") else url
        val params = if (url.contains("?")) url.substringAfter("?") else ""

        val cleanParams = params.split("&").filter {
            it.isNotBlank() &&
                !it.startsWith("page=") &&
                !it.startsWith("orderby=") &&
                !it.startsWith("ascdesc=") &&
                !it.startsWith("filter=") &&
                !it.startsWith("dateline=")
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

        
        if (fetchType == ArticleFetcher.FetchType.FETCH_INIT &&
            mUrl == loadedForumUrl && _articles.value.isNotEmpty()
        ) {
            Timber.tag("[ForumSort]").d("同一板块已加载，跳过重复首拉: $mUrl")
            return
        }

        rewindPageNum()
        fetchArticles(mFetchType) {}
    }

    






    private fun timeType(): String =
        if (orderType != DEFAULT_ORDER) ForumQueryRules.datelineFilter(datelineDays) else DEFAULT_ORDER

    



    private fun extraRequestDelayMs(): Long = when (datelineDays) {
        365 -> 800L
        30, 90 -> 300L
        else -> 0L
    }

    
    private fun allowFallbackRetry(): Boolean = datelineDays != 365

    
    private fun fallbackBackoffMs(): Long = when (datelineDays) {
        30, 90 -> 1200L + (0L..400L).random()
        else -> 800L + (0L..400L).random()
    }

    





    fun selectSort(orderby: String?, name: String) {
        val safe = ForumQueryRules.safeOrderby(orderby)
        val newOrder = ForumQueryRules.orderbyParam(safe)
        
        if (newOrder == orderType && name == _selectedSortName.value) return

        orderType = newOrder
        _selectedSortName.value = name

        prefs.edit().putString(KEY_SORT_NAME, name).apply()

        if (safe == null) {
            datelineDays = null
            _selectedTimeName.value = "不限时间"
            _timeLocked.value = true
        } else {
            _timeLocked.value = false
            if (datelineDays == null) {
                datelineDays = prefs.getInt(KEY_TIME_DAYS, 7)
                _selectedTimeName.value = getTimeName(datelineDays!!)
            }
        }
        restartRefresh()
    }

    



    fun selectTime(days: Int?, name: String) {
        if (_timeLocked.value) {
            Timber.tag("[ForumSort]").w("默认顺序下时间筛选不可用，忽略: $days")
            return
        }
        val safe = ForumQueryRules.safeDateline(days)
        
        if (safe == datelineDays && name == _selectedTimeName.value) return
        datelineDays = safe
        _selectedTimeName.value = name
        if (safe != null) {
            prefs.edit().putInt(KEY_TIME_DAYS, safe).apply()
        }
        restartRefresh()
    }

    



    private fun restartRefresh() {
        refreshJob?.cancel()
        fetching = false
        _isLoading.value = false
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
            ?.replace(Regex("&?(orderby|ascdesc|filter|dateline)=[^&]*"), "")
            ?.trim('&')
            ?: ""
        return "$finalUrl&page=$mCurrentPage$orderType${timeType()}"
    }

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        if (fetching) {
            callback(false)
            return
        }
        fetching = true
        _isLoading.value = true
        if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) {
            _uiState.value = PageUiState.Loading
        }

        val job = viewModelScope.launch(IOPool) {
            try {
                
                delay(extraRequestDelayMs())
                val query = composeUrlByRequestType(fetchType)
                Timber.tag("[ForumSort]").d("请求列表: $query, orderType=$orderType, timeType=${timeType()}")

                var forumPage = getForumArticles(query, fetchType == ArticleFetcher.FetchType.FETCH_INIT)
                var fellBackToDefault = false

                
                if ((forumPage == null || forumPage.articleList.isEmpty()) &&
                    fetchType == ArticleFetcher.FetchType.FETCH_INIT &&
                    orderType != DEFAULT_ORDER &&
                    allowFallbackRetry()
                ) {
                    val savedOrder = orderType
                    orderType = DEFAULT_ORDER
                    val fallbackQuery = composeUrlByRequestType(ArticleFetcher.FetchType.FETCH_INIT)
                    Timber.tag("[ForumSort]").w("排序结果为空，降级默认顺序重试: $fallbackQuery")
                    
                    delay(fallbackBackoffMs())
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
                    } else if (_articles.value.isEmpty()) {
                        _uiState.value = PageUiState.Empty
                    }
                    callback(false)
                } else {
                    if (fellBackToDefault) {
                        showMessage(R.string.sort_fallback_tip)
                    }
                    handleArticleList(fetchType, forumPage.articleList, callback)

                    if (_articles.value.isNotEmpty()) {
                        _uiState.value = PageUiState.Success(_articles.value)
                    }

                    if (!isTabSet) {
                        _threadList.value = forumPage.threadList.ifEmpty { emptyList() }
                        isTabSet = true
                    }
                }
            } catch (e: LoginRequiredException) {
                showMessage(R.string.login_required_error)
                if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) {
                    _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
                }
                callback(false)
            } catch (e: Exception) {
                Timber.e(e, "Fetch forum articles failed")
                val msg = ErrorHandler.getFriendlyMessage(e)
                showMessage(msg)
                if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) {
                    _uiState.value = PageUiState.Error(e, msg)
                }
                callback(false)
            } finally {
                fetching = false
                _isLoading.value = false
            }
        }
        if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) refreshJob = job
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
                loadedForumUrl = mUrl
                callback(true)
            }

            // [ARCH-PHASE-13] 如果新列表为空，直接视为无更多内容
            newList.isEmpty() -> {
                showMessage(R.string.no_more_content)
                callback(false)
            }

            // [ARCH-PHASE-13] 使用 URL 唯一标识进行去重判定，避免标题重复导致的误判
            newList.lastOrNull()?.url == currentList.lastOrNull()?.url -> {
                showMessage(R.string.no_more_content)
                callback(false)
            }

            else -> {
                // 过滤掉可能已经存在的重复帖子（基于 URL）
                val existingUrls = currentList.map { it.url }.toSet()
                val distinctNewList = newList.filter { it.url !in existingUrls }
                
                if (distinctNewList.isEmpty()) {
                    showMessage(R.string.no_more_content)
                    callback(false)
                } else {
                    _articles.value = currentList + distinctNewList
                    callback(true)
                }
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
