package top.easelink.lcg.ui.main.discover.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
        
        // 1. 先创建一个全新的 List 实例，确保 LiveData 能检测到数据变化
        val newList = mutableListOf<DiscoverModel>()
        newList.add(ForumListModel(generateAllForums(context)))
        aggregationModels.postValue(newList)

        viewModelScope.launch(IOPool) {
            runCatching {
                fetchRank(RankType.HEAT, DateType.TODAY).let { ranks ->
                    // 2. 将热榜数据加入刚刚创建的 List 中
                    newList.add(ranks)
                    // 3. 再次传入新的拷贝，触发 UI 刷新
                    aggregationModels.postValue(newList) 
                }
            }.getOrElse {
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