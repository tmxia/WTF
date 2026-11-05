package top.easelink.lcg.ui.main.article.viewmodel

import android.os.Looper
import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener.Companion.FETCH_POST_INIT
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener.Companion.FETCH_POST_MORE
import top.easelink.lcg.ui.main.model.BlockException
import top.easelink.lcg.ui.main.model.NetworkException
import top.easelink.lcg.ui.main.source.local.ArticlesLocalDataSource
import top.easelink.lcg.ui.main.source.model.ArticleAbstractResponse
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource
import top.easelink.lcg.utils.RegexUtils
import top.easelink.lcg.utils.showMessage
import java.io.IOException
import java.util.*

class ArticleViewModel : ViewModel(), ArticleAdapterListener {

    val posts = MutableLiveData<MutableList<Post>>()
    val blockMessage = MutableLiveData<String>()
    val isNotFound = MutableLiveData<Boolean>()
    val shouldDisplayPosts = MutableLiveData<Boolean>()
    val articleTitle = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>()
    val isFavorited = MutableLiveData<Boolean>(false)

    



    val hasMorePages = MutableLiveData(false)

    
    val newPostsAppended = MutableLiveData<List<Post>>()

    
    val postInsertedAt = MutableLiveData<Pair<Int, Post>>()

    private var mUrl: String? = null
    private var nextPageUrl: String? = null
    private var mFormHash: String? = null
    private var articleAbstract: ArticleAbstractResponse? = null

    
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
        safeUpdate(isLoading, true)

        val query: String? = when (type) {
            FETCH_POST_INIT -> mUrl
            FETCH_POST_MORE -> nextPageUrl
            else -> null
        }

        Timber.d("【业务层】开始加载详情/帖子数据, Type=$type, Query=$query")

        if (query.isNullOrBlank()) {
            safeUpdate(isLoading, false)
            callback?.invoke(false)
            return
        }

        fetching = true
        currentJob = viewModelScope.launch(IOPool) {
            try {
                val response = ArticlesRemoteDataSource.getArticleDetail(
                    query, type == FETCH_POST_INIT
                )
                if (response == null) {
                    callback?.invoke(false)
                    return@launch
                }

                Timber.d("【详情页】请求成功: title=${response.articleTitle}, posts数量=${response.postList.size}, hasNextPage=${!response.nextPageUrl.isNullOrEmpty()}")
                articleAbstract = response.articleAbstractResponse
                response.articleTitle.takeIf { it.isNotBlank() }?.let {
                    safeUpdate(articleTitle, it)
                }

                if (response.postList.isNotEmpty()) {
                    when (type) {
                        FETCH_POST_INIT -> {
                            
                            safeUpdate(posts, response.postList.toMutableList())
                                Timber.d("【业务层】初始化数据: 共 ${response.postList.size} 条")
                        }
                        else -> {
                            
                            val currentList = posts.value?.toMutableList() ?: mutableListOf()
                            currentList.addAll(response.postList)
                                Timber.d("【业务层】追加数据: 新增 ${response.postList.size} 条, 当前总计 ${currentList.size} 条")
                            safeUpdate(posts, currentList)
                            safeUpdate(newPostsAppended, response.postList)
                        }
                    }
                }

                nextPageUrl = response.nextPageUrl
                safeUpdate(hasMorePages, nextPageUrl?.isNotEmpty() == true)
                mFormHash = response.fromHash
                safeUpdate(shouldDisplayPosts, true)
                callback?.invoke(true)
            } catch (e: Exception) {
                handleFetchError(e)
                callback?.invoke(false)
            } finally {
                fetching = false
                safeUpdate(isLoading, false)
            }
        }
    }

    private fun handleFetchError(e: Exception) {
        Timber.e(e, "【业务层】详情请求异常: \${e.message}")
        Timber.e("【详情页】处理请求错误，类型: ${e.javaClass.simpleName}, 信息: ${e.message}")
        when (e) {
            is BlockException -> {
                if (posts.value?.isNotEmpty() == true) {
                    showMessage(e.alertMessage)
                } else {
                    setArticleBlocked(e.alertMessage)
                }
            }
            is NetworkException -> setArticleNotFound()
            is IOException -> showMessage(R.string.io_error_mark_invalid)
            else -> showMessage(R.string.error)
        }
        Timber.e(e)
    }

    override fun replyAdd(url: String) {
        if (TextUtils.isEmpty(url)) {
            safeUpdate(isLoading, false)
            throw IllegalStateException()
        }
        currentJob = viewModelScope.launch(IOPool) {
            Timber.d("【点赞】发起请求: $url")
            try {
                val response = ArticlesRemoteDataSource.replyAdd(url)
                Timber.d("【点赞】收到响应: $response")
                showMessage(response)
            } catch (e: Exception) {
                Timber.e(e, "【点赞】发生异常")
                showMessage("点赞异常，请稍后重试")
            }
        }
    }

    fun extractDownloadUrl(): ArrayList<String>? {
        val content = posts.value?.firstOrNull()?.content ?: return null
        
        // 剥离 HTML 标签，只保留纯文本，防止提取失败
        val plainText = org.jsoup.Jsoup.parse(content).text()
        
        // 1. 提取常见的网盘链接
        val linkRegex = Regex("""https?://(?:pan\.baidu\.com/s/|pan\.quark\.cn/s/|www\.aliyundrive\.com/s/|aliyundrive\.com/s/|cloud\.189\.cn/t/|pan\.xunlei\.com/s/|(?:[a-zA-Z0-9.]{0,20})lanzou[a-z]{1}\.com/)[^\s<>"']+""")
        val linkMatches = linkRegex.findAll(plainText).toList()

        // 2. 提取提取码（支持“密码: xxxx”, “提取码：xxxx”, “访问码：xxxx”等格式）
        val codeRegex = Regex("""(?:提取码|密码|访问码|口令)[\s:：]*([a-zA-Z0-9]{4})""")
        val codeMatches = codeRegex.findAll(plainText).toList()

        if (linkMatches.isEmpty() && codeMatches.isEmpty()) {
            return null
        }

        val resultList = ArrayList<String>()

        // 3. 将链接和提取码智能配对并拼装
        val maxCount = maxOf(linkMatches.size, codeMatches.size)
        for (i in 0 until maxCount) {
            val link = linkMatches.getOrNull(i)?.value?.trim() ?: ""
            val code = codeMatches.getOrNull(i)?.groupValues?.get(1)?.trim() ?: ""

            var combined = ""
            if (link.isNotEmpty() && code.isNotEmpty()) {
                combined = "$link 提取码: $code"
            } else if (link.isNotEmpty()) {
                combined = link
            } else if (code.isNotEmpty()) {
                combined = "提取码: $code"
            }

            // 去重处理
            if (combined.isNotEmpty() && !resultList.contains(combined)) {
                resultList.add(combined)
            }
        }

        return if (resultList.isNotEmpty()) resultList else null
    }

    fun checkIsFavorited() {
        val url = mUrl ?: return
        currentJob = viewModelScope.launch(IOPool) {
            val isFav = ArticlesLocalDataSource.isArticleInFavorites(url)
            safeUpdate(isFavorited, isFav)
        }
    }

    fun toggleFavorite() {
        val isCurrentlyFavorited = isFavorited.value ?: false
        if (isCurrentlyFavorited) {
            removeFromFavorite()
        } else {
            addToFavorite()
        }
    }

    fun addToFavorite() {
        val currentPosts = posts.value ?: run {
            showMessage(R.string.add_to_favorite_failed)
            return
        }

        currentJob = viewModelScope.launch(IOPool) {
            try {
                val title = articleTitle.value ?: articleAbstract?.title ?: "未知标题"
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

                if (AppConfig.syncFavorites && UserDataRepo.isLoggedIn) {
                    val threadId = extractThreadId(mUrl)
                    if (threadId != null && mFormHash != null) {
                        val success = ArticlesRemoteDataSource.addFavorites(threadId, mFormHash!!)
                        showMessage(if (success) R.string.sync_favorite_successfully else R.string.sync_favorite_failed)
                    } else {
                        showMessage(R.string.sync_favorite_failed)
                    }
                }

                try {
                    val res = ArticlesLocalDataSource.addArticleToFavorite(articleEntity)
                    if (!AppConfig.syncFavorites) {
                        showMessage(if (res) R.string.add_to_favorite_successfully else R.string.add_to_favorite_failed)
                    }
                    safeUpdate(isFavorited, true)
                } catch (e: Exception) {
                    Timber.e(e)
                    showMessage(R.string.add_to_favorite_failed)
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
                
                if (AppConfig.syncFavorites && UserDataRepo.isLoggedIn) {
                    val threadId = extractThreadId(mUrl)
                    val formHash = mFormHash
                    if (threadId != null && !formHash.isNullOrEmpty()) {
                        val success = ArticlesRemoteDataSource.removeFavorites(threadId, formHash)
                        if (!success) {
                            Timber.w("remote unfavorite failed, falling back to local-only removal")
                        }
                    }
                }

                val res = ArticlesLocalDataSource.delArticleFromFavorite(url)
                showMessage(if (res) R.string.remove_all_favorites_successfully else R.string.add_to_favorite_failed)
                safeUpdate(isFavorited, false)
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.add_to_favorite_failed)
            }
        }
    }

    fun addPostToTop(post: Post) {
        posts.value?.let { currentList ->
            
            val insertPos = if (currentList.isEmpty()) 0 else 1
            currentList.add(insertPos, post)
            safeUpdate(postInsertedAt, insertPos to post)
        }
    }

    private fun setArticleNotFound() {
        safeUpdate(isNotFound, true)
        safeUpdate(shouldDisplayPosts, false)
    }

    private fun setArticleBlocked(message: String) {
        safeUpdate(blockMessage, message)
        safeUpdate(shouldDisplayPosts, false)
    }

    private fun extractThreadId(url: String?): String? {
        return url?.split("-")?.getOrNull(1)
    }

    private fun <T> safeUpdate(liveData: MutableLiveData<T>, value: T) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.value = value
        } else {
            liveData.postValue(value)
        }
    }
}