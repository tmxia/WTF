package top.easelink.lcg.utils

import timber.log.Timber
import top.easelink.lcg.network.JsoupClient

object SignInManager {

    private const val APPLY_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=apply&id=2"
    private const val DRAW_TASK_URL = "https://www.52pojie.cn/home.php?mod=task&do=draw&id=2"

    fun performSignIn(onResult: (String, String) -> Unit) {
        try {
            // 核心：传 false 禁止弹出人机验证，遇到 WAF 直接断定为失败
            val applyResponse = JsoupClient.sendGetRequestWithUrl(APPLY_TASK_URL, enableWafPopup = false)
            val rawHtml = applyResponse.outerHtml()
            Timber.d("签到任务响应: status=${applyResponse.location()}, body=${rawHtml.take(200)}")

            if (rawHtml.contains("您需要先登录")) {
                Timber.e("签到失败：Cookie 已失效，请重新登录")
                onResult("FAILED", "签到失败：登录已失效")
                return
            }

            val applyMessage = applyResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            if (applyMessage.contains("已申请") || applyMessage.contains("已完成")) {
                Timber.d("今日已签到: $applyMessage")
                onResult("ALREADY_DONE", "今日已签到")
                return
            }

            // 发起领取奖励
            val drawResponse = JsoupClient.sendGetRequestWithUrl(DRAW_TASK_URL, enableWafPopup = false)
            Timber.d("领取奖励响应: ${drawResponse.outerHtml().take(200)}")
            val drawText = drawResponse.getElementsByClass("alert_info")
                .first()?.selectFirst("p")?.text().orEmpty()

            Timber.d("签到成功结果: $drawText")
            onResult("SUCCESS", "签到成功！")

        } catch (e: Exception) {
            Timber.e(e, "Jsoup 签到请求异常")
            // 如果是 WAF 拦截，直接提示失败
            if (e.message?.contains("WAF_VERIFICATION_REQUIRED") == true) {
                onResult("FAILED", "签到失败：触发安全风控，请稍后再试")
            } else {
                onResult("FAILED", "签到失败，请检查网络或登录状态")
            }
        }
    }
}
