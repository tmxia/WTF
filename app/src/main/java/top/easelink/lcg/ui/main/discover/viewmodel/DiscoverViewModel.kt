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
        Timber.d("【发现页】开始刷新热榜数据...")
        
        viewModelScope.launch(IOPool) {
            runCatching {
                fetchRank(RankType.HEAT, DateType.TODAY).let { ranks ->
                    // 打印日志，确认数据是否真的拿到了
                    Timber.d("【发现页】热榜请求成功，获取到 ${ranks.listModel.size} 条热榜")
                    
                    // 核心优化：数据拉取成功后，再组装新列表并更新 UI，避免刷新时先清空旧数据导致闪烁
                    val newList = mutableListOf<DiscoverModel>()
                    newList.add(ForumListModel(generateAllForums(context)))
                    newList.add(ranks)
                    aggregationModels.postValue(newList.toMutableList())
                }
            }.getOrElse {
                Timber.e(it, "【发现页】热榜请求异常")
                when (it) {
                    is SocketTimeoutException -> showMessage(R.string.network_error)
                    else -> showMessage(R.string.error)
                }
            }.also {
                isLoading.postValue(false)
            }
        }
    }
}
