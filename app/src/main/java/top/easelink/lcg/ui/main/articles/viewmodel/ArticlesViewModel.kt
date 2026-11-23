package top.easelink.lcg.ui.main.articles.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.repository.ArticlesRepository
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.showMessage
import timber.log.Timber

class ArticlesViewModel : ViewModel(), ArticleFetcher {
    private var mCurrentPage = 0
    private lateinit var mUrl: String

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<Article>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<Article>>> = _uiState.asStateFlow()

    fun initUrl(url: String) {
        mUrl = url
        fetchArticles(ArticleFetcher.FetchType.FETCH_INIT) {}
    }

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        val pageNum: Int = when (fetchType) {
            ArticleFetcher.FetchType.FETCH_MORE -> mCurrentPage + 1
            ArticleFetcher.FetchType.FETCH_INIT -> 1
        }
        _isLoading.value = true
        if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) {
            _uiState.value = PageUiState.Loading
        }
        viewModelScope.launch(IOPool) {
            try {
                val it = ArticlesRepository.getHomePageArticles(mUrl, pageNum)
                if (it.isNotEmpty().also(callback)) {
                    val list = _articles.value.toMutableList()
                    if (fetchType == ArticleFetcher.FetchType.FETCH_MORE && list.isNotEmpty()) {
                        list.addAll(it)
                        _articles.value = list
                    } else {
                        _articles.value = it
                    }

                    mCurrentPage = pageNum
                    _uiState.value = PageUiState.Success(_articles.value)
                } else if (fetchType == ArticleFetcher.FetchType.FETCH_INIT && _articles.value.isEmpty()) {
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.e(e, "fetch home articles failed")
                val msg = ErrorHandler.getFriendlyMessage(e)
                showMessage(msg)
                if (fetchType == ArticleFetcher.FetchType.FETCH_INIT) {
                    _uiState.value = PageUiState.Error(e, msg)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
