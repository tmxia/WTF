package top.easelink.lcg.utils

import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.network.JsoupClient

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val DRAW_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=draw&id=2"

    fun updateLocalSignInTime() {
        SharedPreferencesHelper.getUserSp().edit {
            putLong("sp_key_last_sign_in_at", System.currentTimeMillis())
        }
    }

    fun isSignedToday(): Boolean {
        val sp = SharedPreferencesHelper.getUserSp()
        val lastTime = sp.getLong("sp_key_last_sign_in_at", 0L)
        if (lastTime == 0L) return false
        val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastTime }
        val todayCal = java.util.Calendar.getInstance()
        return lastCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
               lastCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    // 核心修复：彻底移除了 context 参数，修复所有调用方的编译报错
    fun performSignIn(onResult: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
                val applyHtml = applyResponse.outerHtml()

                if (applyHtml.contains("您需要先登录")) {
                    Timber.tag("SignIn").e("签到失败：Cookie 已失效")
                    withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：登录已失效") }
                    return@launch
                }
                if (applyHtml.contains("wzwsquestion") || applyHtml.contains("waf_zw_verify")) {
                    Timber.tag("SignIn").e("签到失败：触发 WAF 拦截")
                    withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：触发安全风控") }
                    return@launch
                }

                val applyMessage = applyResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()

                if (applyMessage.contains("已申请") || applyMessage.contains("已完成") || applyMessage.contains("已签到")) {
                    Timber.tag("SignIn").d("签到结果：今日已签到")
                    updateLocalSignInTime()
                    withContext(Dispatchers.Main) { onResult("ALREADY_DONE", "今日已签到") }
                    return@launch
                }

                val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL)
                val drawHtml = drawResponse.outerHtml()

                if (drawHtml.contains("您需要先登录")) {
                    Timber.tag("SignIn").e("签到失败：Cookie 已失效")
                    withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：登录已失效") }
                    return@launch
                }

                val drawText = drawResponse.getElementsByClass("alert_info")
                    .first()?.selectFirst("p")?.text().orEmpty()

                if (drawText.isBlank()) {
                    Timber.tag("SignIn").e("签到失败：未获取到反馈信息（可能被 WAF 拦截）")
                    withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：未获取到反馈信息") }
                    return@launch
                }
                if (drawText.contains("失败") || drawText.contains("错误") || drawText.contains("异常")) {
                    Timber.tag("SignIn").e("签到结果：$drawText")
                    withContext(Dispatchers.Main) { onResult("FAILED", drawText) }
                    return@launch
                }

                Timber.tag("SignIn").d("签到结果：$drawText")
                updateLocalSignInTime()
                withContext(Dispatchers.Main) { onResult("SUCCESS", "签到成功！") }

            } catch (e: Exception) {
                Timber.tag("SignIn").e("签到异常：${e.message}")
                withContext(Dispatchers.Main) { onResult("FAILED", "签到失败：${e.message}") }
            }
        }
    }
}
