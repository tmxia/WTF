package top.easelink.lcg.utils

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient

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
        try {
            val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
            val applyMessage = applyResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            if (applyMessage.contains("已申请") || applyMessage.contains("已完成")) {
                Timber.d("今日已签到: $applyMessage")
                onResult("ALREADY_DONE", "今日已签到")
                return
            }

            val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL)
            val drawText = drawResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            Timber.d("签到成功结果: $drawText")
            onResult("SUCCESS", "签到成功！")

        } catch (e: Exception) {
            Timber.e(e, "Jsoup 签到请求异常")
            onResult("FAILED", "签到失败，请检查网络或登录状态")
        }
    }
}
