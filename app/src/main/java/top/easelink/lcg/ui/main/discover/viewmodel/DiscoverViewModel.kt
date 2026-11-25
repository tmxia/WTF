package top.easelink.lcg.ui.main.discover.viewmodel

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
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.generateAllForums
import top.easelink.lcg.ui.main.discover.source.DateType
import top.easelink.lcg.ui.main.discover.source.ForumNetworkSource
import top.easelink.lcg.ui.main.discover.source.RankType
import top.easelink.lcg.ui.main.discover.source.fetchRank
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.showMessage
import java.net.SocketTimeoutException

class DiscoverViewModel : ViewModel() {
    private val _aggregationModels = MutableStateFlow<MutableList<DiscoverModel>>(mutableListOf())
    val aggregationModels: StateFlow<MutableList<DiscoverModel>> = _aggregationModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<List<DiscoverModel>>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<List<DiscoverModel>>> = _uiState.asStateFlow()

    @MainThread
    fun initOptions(context: Context) {
        refreshOptions(context)
    }

    @MainThread
    fun refreshOptions(context: Context) {
        _isLoading.value = true
        _uiState.value = PageUiState.Loading
        Timber.d("【发现页】开始刷新热榜与板块数据...")
        viewModelScope.launch(IOPool) {
            runCatching {
                Timber.d("【发现页】开始拉取板块数据...")
                // 并发拉取，消除串行等待时间
                val forumsDeferred = kotlinx.coroutines.async(kotlinx.coroutines.Dispatchers.IO) { ForumNetworkSource.fetchAllForumsFromApi() }
                val ranksDeferred = kotlinx.coroutines.async(kotlinx.coroutines.Dispatchers.IO) { fetchRank(RankType.HEAT, DateType.TODAY) }
                val apiForums = forumsDeferred.await()
                val ranks = ranksDeferred.await()
                Timber.d("【发现页】热榜请求成功，获取到 ${ranks.listModel.size} 条热榜")

                val newList = mutableListOf<DiscoverModel>()
                if (apiForums.isNotEmpty()) {
                    Timber.d("【发现页】使用 API 返回的 ${apiForums.size} 个板块")
                    newList.add(ForumListModel(apiForums))
                } else {
                    Timber.w("【发现页】API 未返回数据，降级使用本地写死的板块")
                    newList.add(ForumListModel(generateAllForums(context)))
                }
                newList.add(ranks)

                _aggregationModels.value = newList.toMutableList()
                if (newList.isEmpty()) {
                    _uiState.value = PageUiState.Empty
                } else {
                    _uiState.value = PageUiState.Success(newList)
                }
            }.getOrElse {
                Timber.e(it, "【发现页】数据拉取异常")
                val msg = ErrorHandler.getFriendlyMessage(it)
                when (it) {
                    is SocketTimeoutException -> showMessage(R.string.network_error)
                    else -> showMessage(msg)
                }
                _uiState.value = PageUiState.Error(it, msg)
            }.also {
                // 移除人工延迟，提高加载速度
                _isLoading.value = false
            }
        }
    }
}
