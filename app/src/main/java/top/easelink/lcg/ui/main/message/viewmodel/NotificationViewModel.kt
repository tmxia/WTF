package top.easelink.lcg.ui.main.message.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.ui.main.model.NotificationModel
import top.easelink.lcg.ui.main.source.remote.NotificationRemoteSource
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.WebsiteConstant.NOTIFICATION_HOME_QUERY

class NotificationViewModel : ViewModel() {
    private val _notifications = MutableStateFlow<NotificationModel?>(null)
    val notifications: StateFlow<NotificationModel?> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PageUiState<NotificationModel>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<NotificationModel>> = _uiState.asStateFlow()

    var nextPageUrl = ""

    fun fetchMoreNotifications(callback: (Boolean) -> Unit) {
        if (nextPageUrl.isEmpty()) {
            callback.invoke(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val model = NotificationRemoteSource.fetchNotifications(nextPageUrl)
                nextPageUrl = model.nextPageUrl
                _notifications.value = model
                callback.invoke(true)
            } catch (e: Exception) {
                Timber.e(e)
                callback.invoke(false)
            }
        }
    }

    fun fetchNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _uiState.value = PageUiState.Loading
            try {
                val model = NotificationRemoteSource.fetchNotifications(NOTIFICATION_HOME_QUERY)
                nextPageUrl = model.nextPageUrl
                _notifications.value = model
                if (model.notifications.isNotEmpty()) {
                    _uiState.value = PageUiState.Success(model)
                } else {
                    _uiState.value = PageUiState.Empty
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            }
            _isLoading.value = false
        }
    }
}
