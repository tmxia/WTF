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
import top.easelink.lcg.utils.AppLogManager
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

        // 崩溃兜底处理器必须在主线程尽早注册：AppGuardStarter 带 @MainThread 校验，
        // 放入 IO 协程会在非主线程抛 IllegalStateException 导致冷启动崩溃。
        AppGuardStarter.init(this@LCGApp)

        // [ARCH-PHASE-15] 将非首屏必须的耗时初始化移入 IO 协程，防止冷启动白屏
        applicationScope.launch {
            initCoil()
            ShiplyInitialization.init(this@LCGApp)
        }

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
