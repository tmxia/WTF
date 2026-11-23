package top.easelink.lcg.ui.main.articles.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.source.repository.ArticlesRepository
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.utils.showMessage

class FavoriteArticlesViewModel : ViewModel(), ArticleFetcher {
    private val _articles = MutableStateFlow<List<ArticleEntity>>(emptyList())
    val articles: StateFlow<List<ArticleEntity>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private var mCurrentPage = 0

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        when (fetchType) {
            ArticleFetcher.FetchType.FETCH_MORE -> {
                // TODO 远端收藏分页：解析下一页链接后按 mCurrentPage+1 请求；当前无翻页数据源。
                _hasMore.value = false
                callback.invoke(false)
                return
            }

            ArticleFetcher.FetchType.FETCH_INIT -> {
                rewindPageNum()
            }
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ArticlesRepository.getAllLocalFavorites().let {
                    if (it.isNotEmpty().also(callback)) {
                        _articles.value = it
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeAllFavorites() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (ArticlesRepository.delAllLocalFavorites()) {
                    _articles.value = emptyList()
                    showMessage(R.string.remove_all_favorites_successfully)
                } else {
                    showMessage(R.string.remove_all_favorites_failed)
                }
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.remove_all_favorites_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFavorite(articleEntity: ArticleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (ArticlesRepository.delLocalFavorite(articleEntity.id)) {
                    val currentList = _articles.value.orEmpty().toMutableList()
                    currentList.remove(articleEntity)
                    _articles.value = currentList
                    showMessage(R.string.remove_all_favorites_successfully)
                } else {
                    showMessage(R.string.remove_all_favorites_failed)
                }
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.remove_all_favorites_failed)
            }
        }
    }

    fun syncFavorites() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val articleEntities = ArticlesRepository.getRemoteFavorites()
                if (articleEntities.isEmpty()) {
                    showMessage(R.string.sync_favorite_failed_empty_favorites)
                } else {
                    if (ArticlesRepository.addAllLocalFavorites(articleEntities)) {
                        _articles.value = ArticlesRepository.getAllLocalFavorites()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
                showMessage(R.string.sync_favorite_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun rewindPageNum() {
        mCurrentPage = 0
    }
}
