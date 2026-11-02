package top.easelink.lcg.account

import androidx.lifecycle.MutableLiveData




object AccountManager {

    val isLoggedIn = MutableLiveData(UserDataRepo.isLoggedIn)

    val isSignedToday = MutableLiveData(UserDataRepo.signInState.isNotEmpty())

    val userInfo = MutableLiveData<UserInfo>()

}