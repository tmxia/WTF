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
import top.easelink.lcg.ui.main.model.LoginRequiredException
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource.getForumArticles
import top.easelink.lcg.utils.WebsiteConstant.FORUM_URL_QUERY
import top.easelink.lcg.utils.showMessage

const val DEFAULT_ORDER = ""

// 板块列表排序白名单（forumdisplay 支持的 orderby 取值：dateline 发帖时间 / lastpost 最后回帖）
private val allowedOrderby = setOf("dateline", "lastpost")

// 时间范围白名单（单位：天），对齐 Discuz forumdisplay 标准档位：1 今天 / 2 两天 / 7 本周 / 30 本月 / 90 三月 / 365 一年；
// 非白名单取值一律拦截，不拼进请求，避免非法参数放大风控特征。
private val allowedDateline = setOf(1, 2, 7, 30, 90, 365)

class ForumArticlesViewModel : ViewModel(), ArticleFetcher {
    private var mUrl: String? = null
    private var mFetchType = ArticleFetcher.FetchType.FETCH_INIT
    private var mCurrentPage = 1
    private var isTabSet = false
    private var fetching = false

    /** 最近一次初始化刷新任务；切换排序/时间时取消未完成的旧任务，只保留最后一次请求。 */
    private var refreshJob: Job? = null

    // 排序与时间筛选仅为当前会话的临时状态，不写入本地持久化；
    // ViewModel 销毁（退出板块 / 重进）后复位为默认顺序、默认一天，避免带进阶筛选参数反复访问被风控画像。
    private var orderType = DEFAULT_ORDER
    private var datelineDays: Int? = 1

    /** 已成功加载过的板块 URL，拦截同一板块的重复首拉，避免 effect 重入/重复事件连发放大风控。 */
    private var loadedForumUrl: String? = null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _selectedSortName = MutableStateFlow("默认顺序")
    val selectedSortName: StateFlow<String> = _selectedSortName.asStateFlow()

    private val _selectedTimeName = MutableStateFlow("一天")
    val selectedTimeName: StateFlow<String> = _selectedTimeName.asStateFlow()

    /** 排序是否被锁定：无时间筛选时，非默认排序不可用（排序以时间筛选为前提）。 */
    private val _sortLocked = MutableStateFlow(datelineDays == null)
    val sortLocked: StateFlow<Boolean> = _sortLocked.asStateFlow()

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _threadList = MutableStateFlow<List<ForumThread>>(emptyList())
    val threadList: StateFlow<List<ForumThread>> = _threadList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

        // 同一板块已加载且已有内容时，跳过重复首拉；排序/时间切换走 restartRefresh，不受此去重影响。
        if (fetchType == ArticleFetcher.FetchType.FETCH_INIT &&
            mUrl == loadedForumUrl && _articles.value.isNotEmpty()
        ) {
            Timber.tag("[ForumSort]").d("同一板块已加载，跳过重复首拉: $mUrl")
            return
        }

        rewindPageNum()
        fetchArticles(mFetchType) {}
    }

    /** 时间范围片段；null（全部时间）时不附加任何过滤参数。 */
    private fun timeType(): String =
        datelineDays?.let { "&filter=dateline&dateline=$it" } ?: DEFAULT_ORDER

    /**
     * 按时间窗口分级的额外请求节流（叠加全局 600ms 最小间隔）：
     * 短窗 1/2/7 天结果新、负载轻，不额外等待；中窗 30/90 天加 300ms；长窗 365 天历史范围重，加 800ms。
     */
    private fun extraRequestDelayMs(): Long = when (datelineDays) {
        365 -> 800L
        30, 90 -> 300L
        else -> 0L
    }

    /** 长窗历史范围重，空结果不再自动降级重试，避免对大时间范围连发第二次请求。 */
    private fun allowFallbackRetry(): Boolean = datelineDays != 365

    /** 降级重试前的退避时长（叠加抖动）：中窗退避更长，短窗从简。 */
    private fun fallbackBackoffMs(): Long = when (datelineDays) {
        30, 90 -> 1200L + (0L..400L).random()
        else -> 800L + (0L..400L).random()
    }

    /**
     * 切换板块列表排序。白名单校验后仅更新当前会话内存状态（不持久化），重置页码并从第 1 页重新加载。
     * orderby 为 null 表示恢复系统默认顺序；离开板块后自动复位默认。
     * 排序以时间筛选为前提：未选时间筛选时，非默认排序被拒绝。
     */
    fun selectSort(orderby: String?, name: String) {
        val safe = orderby?.takeIf { it in allowedOrderby }
        if (safe != null && datelineDays == null) {
            Timber.tag("[ForumSort]").w("未选时间筛选，忽略排序切换: $safe")
            return
        }
        val newOrder = if (safe != null) "&orderby=$safe" else DEFAULT_ORDER
        // 排序未发生变化时短路：不重置页码、不重复发起首拉，避免连点/重复选择放大请求量触发风控
        if (newOrder == orderType && name == _selectedSortName.value) return
        orderType = newOrder
        _selectedSortName.value = name
        restartRefresh()
    }

    /**
     * 切换板块列表时间范围。白名单校验后仅更新当前会话内存状态（不持久化），重置页码并从第 1 页重新加载。
     * days 为 null 表示全部时间（不附加时间过滤）；最大允许 365 天（一年）。
     * 失去时间筛选前提时，排序自动复位回默认顺序。
     */
    fun selectTime(days: Int?, name: String) {
        val safe = days?.takeIf { it in allowedDateline }
        // 时间范围未发生变化时短路，避免重复首拉
        if (safe == datelineDays && name == _selectedTimeName.value) return
        datelineDays = safe
        _selectedTimeName.value = name
        _sortLocked.value = safe == null
        if (safe == null && orderType != DEFAULT_ORDER) {
            orderType = DEFAULT_ORDER
            _selectedSortName.value = "默认顺序"
        }
        restartRefresh()
    }

    /**
     * 取消未完成的旧首拉任务并复位互斥标志，再从第 1 页重新加载；
     * 避免旧任务尚未走到 finally 时 fetching 仍为 true，导致新请求被互斥判断直接丢弃。
     */
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

        val job = viewModelScope.launch(IOPool) {
            try {
                // 按时间窗口分级节流：长窗历史范围重，先额外静默一段再发请求，降低被风控概率
                delay(extraRequestDelayMs())
                val query = composeUrlByRequestType(fetchType)
                Timber.tag("[ForumSort]").d("请求列表: $query, orderType=$orderType, timeType=${timeType()}")

                var forumPage = getForumArticles(query, fetchType == ArticleFetcher.FetchType.FETCH_INIT)
                var fellBackToDefault = false

                // 首拉且带排序时若返回空，自动降级回系统默认顺序重试一次（长窗不重试），避免页面空白卡死
                if ((forumPage == null || forumPage.articleList.isEmpty()) &&
                    fetchType == ArticleFetcher.FetchType.FETCH_INIT &&
                    orderType != DEFAULT_ORDER &&
                    allowFallbackRetry()
                ) {
                    val savedOrder = orderType
                    orderType = DEFAULT_ORDER
                    val fallbackQuery = composeUrlByRequestType(ArticleFetcher.FetchType.FETCH_INIT)
                    Timber.tag("[ForumSort]").w("排序结果为空，降级默认顺序重试: $fallbackQuery")
                    // 降级重试前先退避一小段并叠加抖动，避免两次请求紧贴连发加重风控
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
