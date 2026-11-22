package top.easelink.lcg.ui.main.history.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.easelink.lcg.ui.main.history.model.HistoryModel
import top.easelink.lcg.ui.main.history.source.HistorySource

class HistoryViewModel : ViewModel() {

    val histories: LiveData<List<HistoryModel>> = HistorySource.observeHistories()

    fun clearHistories() {
        viewModelScope.launch { HistorySource.clearHistories() }
    }

    fun deleteHistory(url: String) {
        viewModelScope.launch { HistorySource.deleteHistory(url) }
    }
}
