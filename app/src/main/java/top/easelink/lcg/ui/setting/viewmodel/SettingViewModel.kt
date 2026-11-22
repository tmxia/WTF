package top.easelink.lcg.ui.setting.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.work.WorkManager
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.service.work.SignInWorker

class SettingViewModel(application: Application) : AndroidViewModel(application) {
    private val _autoSignInEnable = MutableStateFlow(false)
    val autoSignInEnable: StateFlow<Boolean> = _autoSignInEnable.asStateFlow()

    private val _syncFavoriteEnable = MutableStateFlow(false)
    val syncFavoriteEnable: StateFlow<Boolean> = _syncFavoriteEnable.asStateFlow()

    private val _searchEngineSelected = MutableStateFlow(0)
    val searchEngineSelected: StateFlow<Int> = _searchEngineSelected.asStateFlow()

    private val _openSearchResultInWebView = MutableStateFlow(false)
    val openSearchResultInWebView: StateFlow<Boolean> = _openSearchResultInWebView.asStateFlow()

    private val _openArticleInWebView = MutableStateFlow(false)
    val openArticleInWebView: StateFlow<Boolean> = _openArticleInWebView.asStateFlow()

    private val _handlePreTagInArticle = MutableStateFlow(false)
    val handlePreTagInArticle: StateFlow<Boolean> = _handlePreTagInArticle.asStateFlow()

    private val _showRecommendFlag = MutableStateFlow(false)
    val showRecommendFlag: StateFlow<Boolean> = _showRecommendFlag.asStateFlow()

    fun init() {
        with(AppConfig) {
            _autoSignInEnable.value = autoSignEnable
            _syncFavoriteEnable.value = syncFavorites
            _searchEngineSelected.value = defaultSearchEngine
            _openSearchResultInWebView.value = searchResultShowInWebView
            _openArticleInWebView.value = articleShowInWebView
            _showRecommendFlag.value = articleShowRecommendFlag
            _handlePreTagInArticle.value = articleHandlePreTag
        }
    }

    fun scheduleJob(enable: Boolean) {
        AppConfig.autoSignEnable = enable
        val context = getApplication<Application>().applicationContext
        if (enable) {
            SignInWorker.startSignInWork(context)
        } else {
            WorkManager.getInstance(context).cancelAllWorkByTag(SignInWorker.TAG)
        }
    }

    fun setSyncFavorite(enable: Boolean) {
        AppConfig.syncFavorites = enable
    }
}
