package top.easelink.lcg.appinit

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import top.easelink.framework.guard.AppGuardStarter
import top.easelink.lcg.BuildConfig
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.config.AppConfig
import top.easelink.lcg.network.LCGCookieJar
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.utils.AppLogManager
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.SignInManager

class LCGApp : Application() {
    private val applicationJob = SupervisorJob()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) Timber.plant(DebugTree())
        Timber.plant(AppLogManager.LogTree())
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        AppLogManager.isCollecting = prefs.getBoolean("debug_mode_enabled", false)

        initCoil()
        AppGuardStarter.init(this)
        ShiplyInitialization.init(this@LCGApp)
        
        applicationScope.launch {
            // 1. 同步本地 CK 到 WebView
            LCGCookieJar.syncToWebView()
            
            // 2. 核心修复：启动时静默校验 CK 是否有效
            attemptSilentLogin()
        }
        
        trySignIn()
        CacheCleanerTask.clearCachesIfNeeded()
    }

    /**
     * 尝试使用本地 CK 静默登录
     */
    private fun attemptSilentLogin() {
        // 如果本地有 auth CK，无论 isLoggedIn 是否是 false，我们都强行试一次
        if (LCGCookieJar.hasLoginCookie()) {
            Timber.d("【静默登录】检测到本地存在登录 CK，正在尝试自动校验...")
            val userInfo = UserInfoRepo.requestUserInfo()
            
            if (userInfo != null && !userInfo.userName.isNullOrBlank()) {
                Timber.d("【静默登录】校验成功！当前登录账号: ${userInfo.userName}")
                UserDataRepo.updateUserInfo(userInfo)
                UserDataRepo.isLoggedIn = true
            } else {
                Timber.w("【静默登录】校验失败，本地 CK 已彻底过期或被风控拦截，需要用户手动登录")
                UserDataRepo.isLoggedIn = false
            }
        } else {
            Timber.w("【静默登录】本地没有任何登录凭证，跳过校验")
            UserDataRepo.isLoggedIn = false
        }
    }

    private fun trySignIn() {
        applicationScope.launch {
            if (!AppConfig.autoSignEnable || !UserDataRepo.isLoggedIn) return@launch
            if (SignInManager.isSignedToday()) {
                Timber.d("今日已签到，无需重复执行")
                return@launch
            }
            delay(2000)
            try {
                SignInManager.performSignIn { status, msg ->
                    Timber.d("App 启动静默签到结果: status=$status, msg=$msg")
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationJob.cancel()
    }

    companion object {
        lateinit var instance: LCGApp
            private set

        @JvmStatic
        val context: Context
            get() = instance
    }
}
