package top.easelink.lcg.utils

import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.ui.main.model.SignInSuccessEvent

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val DRAW_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=draw&id=2"

    fun isSignedToday(): Boolean {
        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        if (lastTime == 0L) return false
        val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastTime }
        val todayCal = java.util.Calendar.getInstance()
        return lastCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
               lastCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun performSignIn(onResult: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Timber.d("开始签到：底层网络请求（自带算法破盾）...")
                
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
                val applyMessage = applyResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()

                if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("已签到")) {
                    markSignInDoneAndNotify()
                    withContext(Dispatchers.Main) { onResult("ALREADY_DONE", "今日已签到") }
                    return@launch
                }

                val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL)
                val drawMessage = drawResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()
                val drawHtml = drawResponse.outerHtml()

                // 核心修复：只要没拿到 alert_info 的提示，就根据关键字判定，不再强制拦截
                if (drawMessage.isBlank()) {
                    if (drawHtml.contains("您需要先登录")) {
                         withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：登录失效") }
                    } else {
                         withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：未获取到签到提示，请检查网络") }
                    }
                    return@launch
                }

                markSignInDoneAndNotify()
                withContext(Dispatchers.Main) {
                    if (drawMessage.contains("成功") || drawMessage.contains("恭喜")) {
                        onResult("SUCCESS", "签到成功！")
                    } else {
                        onResult("SUCCESS", "签到完成：$drawMessage")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "签到异常")
                withContext(Dispatchers.Main) { onResult("FAILED", "签到异常：${e.message}") }
            }
        }
    }

    private suspend fun markSignInDoneAndNotify() {
        SharedPreferencesHelper.getUserSp().edit {
            putLong("sp_key_last_sign_in_at", System.currentTimeMillis())
        }
        try {
            val freshInfo = UserInfoRepo.requestUserInfo()
            if (freshInfo != null) UserDataRepo.updateUserInfo(freshInfo)
        } catch (e: Exception) {
            Timber.e(e, "刷新用户信息失败")
        }
        EventBus.getDefault().post(SignInSuccessEvent())
    }
}
