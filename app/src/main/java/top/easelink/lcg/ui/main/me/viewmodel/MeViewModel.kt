package top.easelink.lcg.ui.main.me.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.account.UserInfo
import top.easelink.lcg.base.PageUiState
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.ui.main.model.AntiScrapingException
import top.easelink.lcg.utils.ErrorHandler
import top.easelink.lcg.utils.showMessage
import java.net.SocketTimeoutException

class MeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PageUiState<UserInfo?>>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState<UserInfo?>> = _uiState.asStateFlow()

    @Suppress("BlockingMethodInNonBlockingContext")
    fun fetchUserInfoDirect() {
        val hasCredential = LCGCookieJar.hasLoginCredential()

        if (!hasCredential) {
            // 无成对凭证：清除陈旧登录标记，进入游客态
            if (UserDataRepo.isLoggedIn) UserDataRepo.isLoggedIn = false
            _uiState.value = PageUiState.Empty
            return
        }

        // 持有凭证：先展示本地缓存资料，整页进入加载态等待远程解析结果
        if (UserDataRepo.isLoggedIn) {
            UserDataRepo.updateUserInfo(
                UserInfo(
                    userName = UserDataRepo.username,
                    avatarUrl = UserDataRepo.avatar,
                    wuaiCoin = UserDataRepo.coin,
                    credit = UserDataRepo.credit,
                    groupInfo = UserDataRepo.group,
                    enthusiasticValue = UserDataRepo.enthusiasticValue,
                    answerRate = UserDataRepo.answerRate,
                    signInStateUrl = null
                )
            )
        }
        _uiState.value = PageUiState.Loading

        viewModelScope.launch(IOPool) {
            try {
                val userInfo = UserInfoRepo.requestUserInfo()

                if (userInfo == null || userInfo == UserInfo.getDefaultUserInfo()) {
                    if (!LCGCookieJar.hasLoginCredential()) {
                        UserDataRepo.isLoggedIn = false
                        _uiState.value = PageUiState.Empty
                    } else {
                        // 持有凭证但未解析到用户信息，保留 CK；按错误态展示并允许重试
                        Timber.w("【安全防御】持有凭证但未解析到用户信息，保留 CK")
                        _uiState.value = PageUiState.Error(
                            AntiScrapingException(),
                            ErrorHandler.getFriendlyMessage(AntiScrapingException())
                        )
                    }
                } else {
                    UserDataRepo.isLoggedIn = true
                    UserDataRepo.updateUserInfo(userInfo)
                    _uiState.value = PageUiState.Success(userInfo)
                }
            } catch (e: Exception) {
                Timber.e(e)
                val msg = when (e) {
                    is SocketTimeoutException -> R.string.network_error
                    is AntiScrapingException -> R.string.anti_scraping_error
                    else -> R.string.general_error
                }
                showMessage(msg)
                _uiState.value = PageUiState.Error(e, ErrorHandler.getFriendlyMessage(e))
            }
        }
    }
}
