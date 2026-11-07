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
            
            LCGCookieJar.syncToWebView()
            
            
            
        }
        
        trySignIn()
        CacheCleanerTask.clearCachesIfNeeded()
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
