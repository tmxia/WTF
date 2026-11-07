package top.easelink.lcg.ui.main.discover.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.generateAllForums
import top.easelink.lcg.ui.main.discover.source.DateType
import top.easelink.lcg.ui.main.discover.source.ForumNetworkSource
import top.easelink.lcg.ui.main.discover.source.RankType
import top.easelink.lcg.ui.main.discover.source.fetchRank
import top.easelink.lcg.utils.showMessage
import java.net.SocketTimeoutException

class DiscoverViewModel : ViewModel() {
    val aggregationModels = MutableLiveData<MutableList<DiscoverModel>>()
    val isLoading = MutableLiveData(false)

    @MainThread
    fun initOptions(context: Context) {
        refreshOptions(context)
    }

    @MainThread
    fun refreshOptions(context: Context) {
        isLoading.value = true
        Timber.d("【发现页】开始刷新热榜与板块数据...")
        viewModelScope.launch(IOPool) {
            runCatching {
                
                Timber.d("【发现页】开始拉取板块数据...")
                val apiForums = ForumNetworkSource.fetchAllForumsFromApi()
                
                
                val ranks = fetchRank(RankType.HEAT, DateType.TODAY)
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
                
                aggregationModels.postValue(newList.toMutableList())
            }.getOrElse {
                Timber.e(it, "【发现页】数据拉取异常")
                when (it) {
                    is SocketTimeoutException -> showMessage(R.string.network_error)
                    else -> showMessage(R.string.error)
                }
            }.also {
                
                kotlinx.coroutines.delay(800)
                isLoading.postValue(false)
            }
        }
    }
}
