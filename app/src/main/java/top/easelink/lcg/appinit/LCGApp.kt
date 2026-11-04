package top.easelink.lcg.appinit

import android.app.Application
import android.content.Context
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
import top.easelink.lcg.service.work.SignInWorker
import top.easelink.lcg.utils.AppLogManager

class LCGApp : Application() {
    private val applicationJob = SupervisorJob()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        
        // 核心修复：无条件挂载日志树，并恢复调试模式开关状态
        Timber.plant(AppLogManager.LogTree())
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        AppLogManager.isCollecting = prefs.getBoolean("debug_mode_enabled", false)
        initCoil()
        AppGuardStarter.init(this)
        ShiplyInitialization.init(this@LCGApp)
        
        applicationScope.launch { LCGCookieJar.syncToWebView() }
        trySignIn()
        CacheCleanerTask.clearCachesIfNeeded()
    }

    private fun trySignIn() {
        applicationScope.launch {
            if (!AppConfig.autoSignEnable || !UserDataRepo.isLoggedIn) return@launch
            
            if (SignInWorker.isRecentlyExecuted()) {
                Timber.d("trySignIn skipped: recently executed")
                return@launch
            }
            delay(2000)
            try {
                
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
