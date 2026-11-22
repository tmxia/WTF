package top.easelink.lcg.ui.main.articles.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource
import top.easelink.lcg.utils.showMessage
import timber.log.Timber

class ArticlesViewModel : ViewModel(), ArticleFetcher {
    private var mCurrentPage = 0
    private lateinit var mUrl: String

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

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
        viewModelScope.launch(IOPool) {
            try {
                ArticlesRemoteDataSource.getHomePageArticles(mUrl, pageNum).let {
                    if (it.isNotEmpty().also(callback)) {
                        val list = _articles.value.toMutableList()
                        if (fetchType == ArticleFetcher.FetchType.FETCH_MORE && list.isNotEmpty()) {
                            list.addAll(it)
                            _articles.value = list
                        } else {
                            _articles.value = it
                        }

                        mCurrentPage = pageNum
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "fetch home articles failed")
                showMessage(R.string.network_error)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
