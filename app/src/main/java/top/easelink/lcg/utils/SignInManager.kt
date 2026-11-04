package top.easelink.lcg.utils

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val DRAW_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=draw&id=2"

    /**
     * 纯 Jsoup 签到方案（底层已带持久化 Cookie 和 400ms 限流防封）
     */
    fun performSignIn(onResult: (String, String) -> Unit) {
        try {
            // 1. 发起申请签到任务
            val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL)
            
            // 判断是否已经签过
            val applyMessage = applyResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            if (applyMessage.contains("已申请") || applyMessage.contains("已完成")) {
                Timber.d("今日已签到: $applyMessage")
                onResult("ALREADY_DONE", "今日已签到")
                return
            }

            // 2. 如果没签过，发起领取奖励任务
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
