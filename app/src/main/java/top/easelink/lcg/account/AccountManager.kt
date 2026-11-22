package top.easelink.lcg.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccountManager {
    private val _isLoggedIn = MutableStateFlow(UserDataRepo.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isSignedToday = MutableStateFlow(UserDataRepo.signInState.isNotEmpty())
    val isSignedToday: StateFlow<Boolean> = _isSignedToday.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    fun setLoggedIn(value: Boolean) {
        _isLoggedIn.value = value
    }

    fun setSignedToday(value: Boolean) {
        _isSignedToday.value = value
    }

    fun setUserInfo(userInfo: UserInfo) {
        _userInfo.value = userInfo
    }
}
