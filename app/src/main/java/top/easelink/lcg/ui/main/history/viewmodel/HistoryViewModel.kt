package top.easelink.lcg.ui.main.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.easelink.lcg.ui.main.history.model.HistoryModel
import top.easelink.lcg.ui.main.history.source.HistorySource

class HistoryViewModel : ViewModel() {

    val histories: StateFlow<List<HistoryModel>> = HistorySource.observeHistories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistories() {
        viewModelScope.launch { HistorySource.clearHistories() }
    }

    fun deleteHistory(url: String) {
        viewModelScope.launch { HistorySource.deleteHistory(url) }
    }
}
