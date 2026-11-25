package top.easelink.lcg.ui.main.article.viewmodel

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener.Companion.FETCH_POST_INIT
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener.Companion.FETCH_POST_MORE
import top.easelink.lcg.ui.main.article.source.DownloadLinkRules
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.model.LikeFailedEvent
import top.easelink.lcg.ui.main.model.LikeSuccessEvent
import top.easelink.lcg.ui.main.model.NetworkException
import top.easelink.lcg.ui.main.source.model.ArticleAbstractResponse
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.main.source.local.LikeStateStore
import top.easelink.lcg.ui.main.source.repository.ArticlesRepository
import org.greenrobot.eventbus.EventBus
import top.easelink.lcg.utils.LikeRules
import top.easelink.lcg.utils.RegexUtils
import top.easelink.lcg.utils.showMessage
import top.easelink.lcg.utils.ErrorHandler
import java.io.IOException

class ArticleViewModel : ViewModel(), ArticleAdapterListener {

    private val _posts = MutableStateFlow<MutableList<Post>>(mutableListOf())
    val posts: StateFlow<MutableList<Post>> = _posts.asStateFlow()

    private val _blockMessage = MutableStateFlow("")
    val blockMessage: StateFlow<String> = _blockMessage.asStateFlow()

    private val _isNotFound = MutableStateFlow(false)
    val isNotFound: StateFlow<Boolean> = _isNotFound.asStateFlow()

    private val _shouldDisplayPosts = MutableStateFlow(true)
    val shouldDisplayPosts: StateFlow<Boolean> = _shouldDisplayPosts.asStateFlow()

    private val _articleTitle = MutableStateFlow("")
    val articleTitle: StateFlow<String> = _articleTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<top.easelink.lcg.base.PageUiState<List<Post>>>(top.easelink.lcg.base.PageUiState.Loading)
    val uiState: StateFlow<top.easelink.lcg.base.PageUiState<List<Post>>> = _uiState.asStateFlow()

    private val _isFavorited = MutableStateFlow(false)
    val isFavorited: StateFlow<Boolean> = _isFavorited.asStateFlow()

    private val _hasMorePages = MutableStateFlow(false)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _newPostsAppended = MutableStateFlow<List<Post>>(emptyList())
    val newPostsAppended: StateFlow<List<Post>> = _newPostsAppended.asStateFlow()

    private val _postInsertedAt = MutableStateFlow<Pair<Int, Post>?>(null)
    val postInsertedAt: StateFlow<Pair<Int, Post>?> = _postInsertedAt.asStateFlow()

    private var mUrl: String? = null
    private var nextPageUrl: String? = null
    private var mFormHash: String? = null
    private var articleAbstract: ArticleAbstractResponse? = null

    
    private var loadedUrl: String? = null

    
    private var forceReload = false

    private var fetching = false
    private var currentJob: Job? = null

    fun setUrl(url: String) {
        mUrl = url
    }

    override fun fetchArticlePost(type: Int, callback: ((Boolean) -> Unit)?) {
        Timber.d("【详情页】发起请求: type=$type, currentUrl=$mUrl, nextPageUrl=$nextPageUrl")
        if (fetching) {
            Timber.w("【详情页】请求被忽略：已有任务正在 fetching 中")
            callback?.invoke(false)
            return
        }
        safeUpdate(_isLoading, true)

        val query: String? = when (type) {
            FETCH_POST_INIT -> mUrl
            FETCH_POST_MORE -> nextPageUrl
            else -> null
        }

        Timber.d("【业务层】开始加载详情/帖子数据, Type=$type, Query=$query")

        if (query.isNullOrBlank()) {
            safeUpdate(_isLoading, false)
            callback?.invoke(false)
            return
        }

        
        if (type == FETCH_POST_INIT && !forceReload && mUrl != null && mUrl == loadedUrl && _posts.value.isNotEmpty()) {
            Timber.d("【详情页】同一帖子已加载，跳过重复请求: $mUrl")
            safeUpdate(_isLoading, false)
            callback?.invoke(true)
            return
        }

        if (type == FETCH_POST_INIT) {
            safeUpdate(_uiState, top.easelink.lcg.base.PageUiState.Loading)
        }
        fetching = true
        currentJob = viewModelScope.launch(IOPool) {
            try {
                val response = ArticlesRepository.getArticleDetail(
                    query, type == FETCH_POST_INIT
                )
                if (response == null) {
                    if (type == FETCH_POST_INIT) {
                        safeUpdate(_uiState, top.easelink.lcg.base.PageUiState.Empty)
                    }
                    callback?.invoke(false)
                    return@launch
                }

                Timber.d("【详情页】请求成功: title=${response.articleTitle}, posts数量=${response.postList.size}, hasNextPage=${!response.nextPageUrl.isNullOrEmpty()}")
                articleAbstract = response.articleAbstractResponse
                response.articleTitle.takeIf { it.isNotBlank() }?.let {
                    safeUpdate(_articleTitle, it)
                }

                if (response.postList.isNotEmpty()) {
                    // 恢复本机已确认成功的点赞（服务端返回成功才落库，不是纯乐观态）
                    response.postList.forEach { post ->
                        if (!post.isLiked) {
                            post.isLiked = post.pid?.let { LikeStateStore.isLiked(it) } == true
                        }
                    }
                    when (type) {
                        FETCH_POST_INIT -> {
                            safeUpdate(_posts, response.postList.toMutableList())
                            loadedUrl = mUrl
                            Timber.d("【业务层】初始化数据: 共 ${response.postList.size} 条")
                        }
                        else -> {
                            val currentList = _posts.value.toMutableList()
                            currentList.addAll(response.postList)
                            Timber.d("【业务层】追加数据: 新增 ${response.postList.size} 条, 当前总计 ${currentList.size} 条")
                            safeUpdate(_posts, currentList)
                            safeUpdate(_newPostsAppended, response.postList)
                        }
                    }
                }

                if (type == FETCH_POST_INIT) {
                    if (_posts.value.isNotEmpty()) {
                        safeUpdate(_uiState, top.easelink.lcg.base.PageUiState.Success(_posts.value))
                    } else {
                        safeUpdate(_uiState, top.easelink.lcg.base.PageUiState.Empty)
                    }
                }

                nextPageUrl = response.nextPageUrl
                safeUpdate(_hasMorePages, nextPageUrl?.isNotEmpty() == true)
                mFormHash = response.fromHash
                safeUpdate(_shouldDisplayPosts, true)
                callback?.invoke(true)
            } catch (e: Exception) {
                handleFetchError(e)
                if (type == FETCH_POST_INIT && _posts.value.isEmpty() &&
                    e !is BlockException && e !is NetworkException) {
                    val msg = ErrorHandler.getFriendlyMessage(e)
                    safeUpdate(_uiState, top.easelink.lcg.base.PageUiState.Error(e, msg))
                }
                callback?.invoke(false)
            } finally {
                fetching = false
                forceReload = false
                safeUpdate(_isLoading, false)
            }
        }
    }

    
    fun forceRefresh() {
        forceReload = true
        fetchArticlePost(FETCH_POST_INIT, null)
    }

    private fun handleFetchError(e: Exception) {
        // [ARCH-PHASE-12] 修复日志转义符，并接入统一 ErrorHandler
        Timber.e(e, "【业务层】详情请求异常: ${e.message}")
        Timber.e("【详情页】处理请求错误，类型: ${e.javaClass.simpleName}, 信息: ${e.message}")
        
        when (e) {
            is BlockException -> {
                if (_posts.value.isNotEmpty()) {
                    showMessage(e.alertMessage)
                } else {
                    setArticleBlocked(e.alertMessage)
                }
            }
            is NetworkException -> setArticleNotFound()
            else -> {
                // 使用 ErrorHandler 统一翻译断网、超时、WAF 等异常
                showMessage(ErrorHandler.getFriendlyMessage(e))
            }
        }
    }

    override fun replyAdd(url: String) {
        if (TextUtils.isEmpty(url)) {
            safeUpdate(_isLoading, false)
            throw IllegalStateException()
        }
        currentJob = viewModelScope.launch(IOPool) {
            Timber.d("【点赞】发起请求: $url")
            try {
                val response = ArticlesRepository.replyAdd(url)
                Timber.d("【点赞】收到响应: $response")
                val pid = LikeRules.pidFromUrl(url)
                when {
                    // 站点已赞过：幂等成功，直接落状态，不再重复提示失败
                    LikeRules.isAlreadyLiked(response) -> {
                        LikeStateStore.markLiked(pid)
                        showMessage(response)
                        EventBus.getDefault().post(LikeSuccessEvent(url))
                    }
                    LikeRules.isSuccessMessage(response) -> {
                        LikeStateStore.markLiked(pid)
                        showMessage(response)
                        EventBus.getDefault().post(LikeSuccessEvent(url))
                    }
                    else -> {
                        // 服务端明确失败或无法判定：撤销乐观置亮，提示原因
                        showMessage(response.ifBlank { "点赞失败，请稍后重试" })
                        EventBus.getDefault().post(LikeFailedEvent(url))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "【点赞】发生异常")
                showMessage("点赞异常，请稍后重试")
                EventBus.getDefault().post(LikeFailedEvent(url))
            }
        }
    }

    fun extractDownloadUrl(): ArrayList<String>? {
        val content = _posts.value.firstOrNull()?.content ?: return null
        val links = DownloadLinkRules.extract(content)
        return if (links.isEmpty()) null else ArrayList(links)
    }

    fun checkIsFavorited() {
        val url = mUrl ?: return
        currentJob = viewModelScope.launch(IOPool) {
            val isFav = ArticlesRepository.isLocalFavorite(url)
            safeUpdate(_isFavorited, isFav)
        }
    }

    fun toggleFavorite() {
        val isCurrentlyFavorited = _isFavorited.value
        if (isCurrentlyFavorited) {
            removeFromFavorite()
        } else {
            addToFavorite()
        }
    }

    fun addToFavorite() {
        val currentPosts = _posts.value.ifEmpty {
            showMessage(R.string.add_to_favorite_failed)
            return
        }
        currentJob = viewModelScope.launch(IOPool) {
            try {
                val title = _articleTitle.value.ifEmpty { articleAbstract?.title ?: "未知标题" }
                val author = currentPosts.firstOrNull()?.author ?: ""
                val content = articleAbstract?.description ?: ""
                val url = mUrl ?: throw IllegalStateException("URL is null")

                val articleEntity = ArticleEntity(
                    title = title,
                    author = author,
                    url = url,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )

                val localRes = ArticlesRepository.addLocalFavorite(articleEntity)
                safeUpdate(_isFavorited, true)

                if (AppConfig.syncFavorites && LCGCookieJar.hasLoginCredential()) {
                    val threadId = extractThreadId(mUrl)
                    val formHash = mFormHash
                    if (threadId != null && formHash != null) {
                        val success = ArticlesRepository.addFavoriteRemote(threadId, formHash)
                        if (!success) {
                            showMessage(R.string.sync_favorite_failed)
                        } else {
                            showMessage(R.string.sync_favorite_successfully)
                        }
                    } else {
                        showMessage(R.string.sync_favorite_failed)
                    }
                } else if (localRes) {
                    showMessage(R.string.add_to_favorite_successfully)
                }
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.add_to_favorite_failed)
            }
        }
    }

    fun removeFromFavorite() {
        val url = mUrl ?: return
        currentJob = viewModelScope.launch(IOPool) {
            try {
                if (AppConfig.syncFavorites && LCGCookieJar.hasLoginCredential()) {
                    val threadId = extractThreadId(mUrl)
                    val formHash = mFormHash
                    if (threadId != null && !formHash.isNullOrEmpty()) {
                        val success = ArticlesRepository.removeFavoriteRemote(threadId, formHash)
                        if (!success) {
                            Timber.w("remote unfavorite failed, falling back to local-only removal")
                        }
                    }
                }

                val res = ArticlesRepository.delLocalFavorite(url)
                showMessage(if (res) R.string.remove_all_favorites_successfully else R.string.add_to_favorite_failed)
                safeUpdate(_isFavorited, false)
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.add_to_favorite_failed)
            }
        }
    }

    fun addPostToTop(post: Post) {
        _posts.value.let { currentList ->
            val insertPos = if (currentList.isEmpty()) 0 else 1
            currentList.add(insertPos, post)
            safeUpdate(_postInsertedAt, insertPos to post)
        }
    }

    
    fun appendNewReply(post: Post) {
        val currentList = _posts.value.toMutableList()
        currentList.add(post)
        safeUpdate(_posts, currentList)
        safeUpdate(_shouldDisplayPosts, true)
    }

    private fun setArticleNotFound() {
        safeUpdate(_isNotFound, true)
        safeUpdate(_shouldDisplayPosts, false)
    }

    private fun setArticleBlocked(message: String) {
        safeUpdate(_blockMessage, message)
        safeUpdate(_shouldDisplayPosts, false)
    }

    private fun extractThreadId(url: String?): String? {
        return url?.split("-")?.getOrNull(1)
    }

    private fun <T> safeUpdate(flow: MutableStateFlow<T>, value: T) {
        flow.value = value
    }
}
