package top.easelink.lcg.ui.main.me.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber
import top.easelink.framework.threadpool.IOPool
import top.easelink.lcg.R
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.account.UserInfo
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.ui.main.model.AntiScrapingException
import top.easelink.lcg.utils.showMessage
import java.net.SocketTimeoutException

class MeViewModel : ViewModel() {
    @Suppress("BlockingMethodInNonBlockingContext")
    fun fetchUserInfoDirect() {
        val hasCredential = LCGCookieJar.hasLoginCredential()

        if (!hasCredential) {
            // 网络层无 auth 凭证：清除陈旧的本地登录态，避免沿用过期缓存按已登录展示
            if (UserDataRepo.isLoggedIn) UserDataRepo.isLoggedIn = false
        } else if (UserDataRepo.isLoggedIn) {
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

        viewModelScope.launch(IOPool) {
            try {
                val userInfo = UserInfoRepo.requestUserInfo()

                if (userInfo == null || userInfo == UserInfo.getDefaultUserInfo()) {
                    // 页面返回游客态：仅在网络层也无凭证时才判定未登录，避免解析抖动误清有效会话
                    if (!LCGCookieJar.hasLoginCredential()) {
                        UserDataRepo.isLoggedIn = false
                    } else {
                        Timber.w("【安全防御】持有凭证但未解析到用户信息，保留 CK！")
                    }
                } else {
                    UserDataRepo.isLoggedIn = true
                    UserDataRepo.updateUserInfo(userInfo)
                }
            } catch (e: Exception) {
                Timber.e(e)
                when (e) {
                    is SocketTimeoutException -> R.string.network_error
                    is AntiScrapingException -> R.string.anti_scraping_error
                    else -> R.string.general_error
                }.let {
                    showMessage(it)
                }
            }
        }
    }
}
