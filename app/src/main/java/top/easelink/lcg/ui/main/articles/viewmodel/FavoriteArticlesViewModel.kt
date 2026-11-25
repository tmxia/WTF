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
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.ui.main.source.model.ArticleEntity
import top.easelink.lcg.ui.main.source.repository.ArticlesRepository
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.showMessage

class FavoriteArticlesViewModel : ViewModel(), ArticleFetcher {
    private val _articles = MutableStateFlow<List<ArticleEntity>>(emptyList())
    val articles: StateFlow<List<ArticleEntity>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<ArticleEntity>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<ArticleEntity>>> = _uiState.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** 读取网站收藏只看登录态：网站收藏是权威数据，进入页面就应该能拉到。 */
    private fun canReadWeb(): Boolean = LCGCookieJar.hasLoginCredential()

    /** 回写网站（删除/取消收藏）受"同步到网站"开关控制。 */
    private fun canWriteWeb(): Boolean = AppConfig.syncFavorites && LCGCookieJar.hasLoginCredential()

    override fun fetchArticles(fetchType: ArticleFetcher.FetchType, callback: (Boolean) -> Unit) {
        when (fetchType) {
            ArticleFetcher.FetchType.FETCH_MORE -> {
                // 远端收藏一次性拉全量，无更多页
                _hasMore.value = false
                callback.invoke(false)
                return
            }

            ArticleFetcher.FetchType.FETCH_INIT -> Unit
        }

        _isLoading.value = true
        _uiState.value = PageUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 先给本地缓存，保证首屏即有内容
                val local = ArticlesRepository.getAllLocalFavorites()
                publish(local)

                // 再与网站 API 对齐：拉取远端收藏并整体覆盖本地，网站侧删除的条目同步消失
                if (canReadWeb()) {
                    runCatching { ArticlesRepository.syncRemoteFavorites() }
                        .onSuccess { synced ->
                            publish(synced)
                            callback.invoke(synced.isNotEmpty())
                        }
                        .onFailure {
                            // 远端失败（被盾 / 未登录 / 网络）时保留本地，不误清空，并把原因告知用户
                            Timber.w(it, "同步网站收藏失败，保留本地缓存")
                            showMessage(
                                "网站收藏同步失败：" +
                                    ErrorHandler.getFriendlyMessage(it)
                            )
                        }
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun publish(list: List<ArticleEntity>) {
        _articles.value = list
        _uiState.value = if (list.isEmpty()) PageUiState.Empty else PageUiState.Success(list)
    }

    fun removeAllFavorites() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val local = ArticlesRepository.getAllLocalFavorites()
                if (canWriteWeb() && local.isNotEmpty()) {
                    // 逐条按站点删除链接取消收藏，任何一条失败都不清空本地，避免"本地没了网站还在"
                    // delUrl 为空表示"仅本地收藏、未同步网站"，只需删本地；有 delUrl 的才需要回写站点
                    val failed = local.count { entity ->
                        entity.delUrl.isNotBlank() &&
                            !ArticlesRepository.removeFavoriteByDelUrl(entity.delUrl)
                    }
                    if (failed > 0) {
                        showMessage(R.string.favorite_remote_remove_failed)
                        return@launch
                    }
                }
                if (ArticlesRepository.delAllLocalFavorites()) {
                    _articles.value = emptyList()
                    _uiState.value = PageUiState.Empty
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
                if (canWriteWeb() && articleEntity.delUrl.isNotBlank()) {
                    if (!ArticlesRepository.removeFavoriteByDelUrl(articleEntity.delUrl)) {
                        showMessage(R.string.favorite_remote_remove_failed)
                        return@launch
                    }
                }
                if (ArticlesRepository.delLocalFavorite(articleEntity.id)) {
                    val currentList = _articles.value.orEmpty().toMutableList()
                    currentList.remove(articleEntity)
                    publish(currentList)
                    showMessage(R.string.remove_favorite_successfully)
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
        if (!canReadWeb()) {
            showMessage(R.string.sync_favorite_failed)
            return
        }
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val synced = ArticlesRepository.syncRemoteFavorites()
                publish(synced)
                showMessage(
                    if (synced.isEmpty()) R.string.favorite_sync_empty_remote
                    else R.string.sync_favorite_successfully
                )
            } catch (e: Exception) {
                Timber.e(e, "手动同步收藏失败")
                showMessage(R.string.sync_favorite_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
