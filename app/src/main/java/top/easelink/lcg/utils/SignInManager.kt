package top.easelink.lcg.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.network.JsoupClient
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.ui.main.me.source.UserInfoRepo
import top.easelink.lcg.ui.main.model.SignInSuccessEvent
import org.greenrobot.eventbus.EventBus

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
                val applyHtml = applyResponse.outerHtml()

                if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("已签到")) {
                    refreshUserInfoAndBroadcast()
                    withContext(Dispatchers.Main) { onResult("ALREADY_DONE", "今日已签到") }
                    return@launch
                }

                val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL)
                val drawMessage = drawResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()
                val drawHtml = drawResponse.outerHtml()

                if (applyMessage.isBlank() && drawMessage.isBlank() && WafSolver.isWafPage(drawHtml)) {
                    withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：WAF 盾拦截未能解除") }
                    return@launch
                }

                refreshUserInfoAndBroadcast()
                withContext(Dispatchers.Main) {
                    if (drawMessage.contains("成功") || drawMessage.contains("恭喜")) {
                        onResult("SUCCESS", "签到成功！")
                    } else {
                        onResult("SUCCESS", "签到完成：${drawMessage.take(20)}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "签到异常")
                withContext(Dispatchers.Main) { onResult("FAILED", "签到异常：${e.message}") }
            }
        }
    }

    private suspend fun refreshUserInfoAndBroadcast() {
        try {
            val freshInfo = UserInfoRepo.requestUserInfo()
            if (freshInfo != null) {
                UserDataRepo.updateUserInfo(freshInfo)
            }
        } catch (e: Exception) {
            Timber.e(e, "刷新用户信息失败")
        }
        EventBus.getDefault().post(SignInSuccessEvent())
    }
}
